// license GPL
// by www.ixi-audio.net

/*
TxalaSet.new(s, thisProcess.nowExecutingPath.dirname++"/sounds/")
*/



TxalaSet{

	var attacktime = 0.005; // THIS IS A SAFE GAP ARTIFICIALLY ENTERED TO AVOID CHOPPING THE START OF THE HITS
	var numplanks = 6; // max planks
	var plankresolution = 5; // max positions per plank 0-4
	var bufflength = 10; // secs
	var planksamplebuttons;// Array.fillND([numplanks, plankresolution], { nil });
	var names; // = ["A","B","C","D","E","F","G","H"];
	var gridloc; // = Point.new(50,50);
	var sndpath; // = thisProcess.nowExecutingPath.dirname++"/sounds/";
	var sttime;
	var processbutton, processflag=false, hit;
	var recbuf;
	var win, scope, namefield, numhits; //namefieldstr
	var server, onsetsynth, silencesynth, recsynth;
	var respOSC, silOSC;
	var onsets, silences, recthis;
	var params;
	var meter, meterOSC, metersynth;
	var helpwin, basepath;


	*new { | server, sndpath, params, basepath |
		^super.new.initTxalaSet(server, sndpath, params, basepath);
	}

	initTxalaSet { |aserver, asndpath, someparams, apath|

		server = aserver;
		sndpath = asndpath;
		params = someparams;
		basepath = apath;

		if (win.isNil.not, {win.close});

		planksamplebuttons =  Array.fillND([numplanks, plankresolution], { nil });

		names = ["A","B","C","D","E","F","G","H"];
		gridloc = Point.new(60,100);

		respOSC.free ;
		silOSC.free ;
		meterOSC.free;
		metersynth.free;

		recbuf = Buffer.alloc(server, 44100 * bufflength, 1); // mono buffer

		SynthDef(\meterdisplay, {
			var delimp, in;
			//in = SoundIn.ar(0);
			// delimp = Delay1.kr(in);
			// measure rms and Peak
			SendPeakRMS.kr(SoundIn.ar(0), 10, 0, "/levels")
			//SendReply.kr(Impulse.kr(10), '/levels',
				//[Amplitude.kr(in), K2A.ar(Peak.ar(in, delimp).lag(0, 3))]);
		}).add;

		// just dumps the sound in signal into the bufnum buffer for later processing at end()
		SynthDef(\tx_recBuf,{ arg in=0, bufnum=0;
			RecordBuf.ar(SoundIn.ar(in), bufnum);
		}).add;

		// look for onsets
		SynthDef(\tx_onset_listener, { arg in=0, thresh = 0.2, relaxtime = 1, comp_thres=0.3;
			var loc,chain, signal;
			signal = SoundIn.ar(in);
			signal = Compander.ar(signal, signal, // expand loud sounds and get rid of low ones
				thresh: comp_thres,// THIS IS CRUCIAL. in RMS
				slopeBelow: 1.9, // almost noise gate
				slopeAbove: 1.1, // >1 to get expansion
				clampTime: 0.005,
				relaxTime: 0.01
			);
			loc = LocalBuf(1024, 1) ;
			chain = FFT(loc, signal);
			SendTrig.kr(Onsets.kr(chain, thresh, relaxtime:relaxtime), 999, Loudness.kr(chain));
		}).add ;

		// detect silences
		SynthDef(\tx_silence_detection, { arg in=0, amp=0.005, comp_thres=0.3;
			var signal = SoundIn.ar(in);
			signal = Compander.ar(signal, signal, // expand loud sounds and get rid of low ones
				thresh: comp_thres,// THIS IS CRUCIAL. in RMS
				slopeBelow: 1.9, // almost noise gate
				slopeAbove: 1.1, // >1 to get expansion
				clampTime: 0.005,
				relaxTime: 0.01
			);
			SendTrig.kr(A2K.kr(DetectSilence.ar(signal, amp:amp)), 111, 0);
		}).add ;

		this.doGUI();

		{
			// listens for hits. wait until synths are ready
			onsetsynth = Synth(\tx_onset_listener, [\in, params.in,\threshold, params.onset.threshold, \comp_thres, params.tempo.comp_thres]) ;
			silencesynth = Synth.newPaused(\tx_silence_detection, [\in, params.in, \amp, params.tempo.threshold, \comp_thres, params.tempo.comp_thres]);
			// two responders
			respOSC = OSCFunc({ arg msg, time; //ATTACK
				if (msg[2] == 999){
					if (processflag, { onsets = onsets.add(time-sttime) }) ;
					//("attack detected").postln;
					{processbutton.value = 2}.defer;// detected
					silencesynth.run ; // now look for end of hit
					onsetsynth.run(false) ;
				}
			},'/tr', Server.local.addr);

			silOSC = OSCFunc({ arg msg, time; //RELEASE
				if (msg[2] == 111){
					if (processflag, {
						silences = silences.add(time-sttime);
						{ numhits.string =  (numhits.string.asInt + 1).asString }.defer; //counter++
					});
					{ processbutton.value = processflag.asInt }.defer; // back to state 0 or 1
					//("silence detected").postln ;
					onsetsynth.run ; // now look for begging of a new hit
					silencesynth.run(false) ;
				}
			},'/tr', Server.local.addr);

			metersynth = Synth(\meterdisplay);
			meterOSC = OSCFunc({arg msg;
				{
					if (meter.isNil.not, {
						meter.peakLevel = msg[3].ampdb.linlin(-80, 0, 0, 1);
						meter.value = msg[4].ampdb.linlin(-80, 0, 0, 1);
					});
				}.defer;
			}, '/levels', server.addr);
		}.defer(1);
	}



	doGUI {

		win = Window.new(~txl.do("Plank set manager"), Rect(10, 100, 270, 260));
		win.onClose_({
			var destpath, filename, data;
			this.clean();
			respOSC.free;
			silOSC.free;
			meterOSC.free;
			onsetsynth.free;
			silencesynth.free;
			recsynth.free;
			metersynth.free;
		});

		StaticText(win, Rect(gridloc.x-50, gridloc.y-25, 80, 25)).string = ~txl.do("Locs >");
		numplanks.do({arg indexA;

			plankresolution.do({arg indexB;
				var name = (indexA+1).asString++names[indexB];
				if (indexA==0, {
					StaticText(win, Rect(gridloc.x+35+(indexB*30), gridloc.y-25, 50, 25)).string = names[indexB];
				});
				StaticText(win, Rect(gridloc.x-50, (indexA*25)+gridloc.y, 50, 25)).string = ~txl.do("Plank")+(indexA+1).asString;

				planksamplebuttons[indexA][indexB] = Button(win, Rect((30*indexB)+gridloc.x+25, (indexA*25)+gridloc.y, 30, 25))
				.states_([
					[name, Color.white, Color.black],
					[name, Color.black, Color.red]
				])
				.action_({ arg butt;
					if (butt.value.asBoolean, {
						recthis = [indexA, indexB];
						this.process(); // Task that processes the sound in realtime
						{ butt.valueAction_(0) }.defer(bufflength); //auto go OFF
					}, {
						butt.states = [[name, Color.red, Color.black], [name, Color.black, Color.red]];
						butt.value = 0;
					})
				});

			});
		});


		StaticText(win, Rect(10, 42, 100, 25)).string = ~txl.do("set name");

		namefield = TextField(win, Rect(60, 42, 140, 25)).value = Date.getDate.stamp;

		Button(win, Rect(10,10, 70, 25))
		.states_([
			[~txl.do("reset"), Color.white, Color.black]
		])
		.action_({ arg butt;
			planksamplebuttons.flat.do({ arg but;
				but.states = [[but.states[0][0], Color.white, Color.black], [but.states[1][0], Color.black, Color.red]]
			})
		});

/*		Button(win, Rect(80,10, 70, 25))
		.states_([
			[~txl.do("HELP"), Color.white, Color.black]
		])
		.action_({ arg butt;
			var ww;
			ww = Window.new(~txl.do("Help"), Rect(0, 0, 335, 365));
			StaticText(ww, Rect(10, 10, 325, 365)).string = ~txl.do("Each row represents a plank (1,2,3,4,5,6).
Each button in the row represents a position within each plank (A,B,C,D,E).
Ideally those positions go left to right from the edge until the center of each plank.
Give a name to the set before recording the sounds or the set will be named after the date and time.
Select one of the positions (eg 1A) by pressing the corresponding button, then you have 10 secs to hit several times in the same plank location with different amplitudes (low to high). Make sure you leave time for each hit's tile to finish before hitting again. Repeat this procedure for each of the positions in each of the planks. You dont have to fill the five positions, in fact one position per plank is enough, but the more hits the richer the output of the Interactive txalaparta will sound.
			After 10 secs the program will process the recordings and try to detect, cut, normalise and save each of the hits into a separated file. It is not a bad idea to open the files in a sound editor (eg. Audacity) to see if they are correct, the system is not perfect!");
			ww.front
		});
		*/

		Button( win, Rect(80,10, 70, 25))
		.states_([
			[~txl.do("help"), Color.white, Color.black],
		])
		.action_({ arg but;
			var langst = "", path, file; // eu
			path = basepath[..basepath.findBackwards(Platform.pathSeparator.asString)]; // get rid of last folder
			if (~txl.lang==0, {langst = "_en"});
			if (~txl.lang==1, {langst = "_es"});
			file = path++"documentation/index"++langst++".html";
			[file, path].postln;
			helpwin = WebView().front.url_(file)
		});

		processbutton = Button(win, Rect(150,10, 68, 25))
		.states_([
			[~txl.do("processing"), Color.white, Color.grey],
			[~txl.do("processing"), Color.red, Color.grey],
			[~txl.do("processing"), Color.white, Color.red]
		]);

		numhits = StaticText(win, Rect(220, 10, 30, 25)).string = "0";

		meter = LevelIndicator(win, Rect(250, 110, 8, 140)).drawsPeak_(true);

		win.front;
	}

	clean {
		onsetsynth.free;
		silencesynth.free;
		recsynth.free;

		respOSC.free ;
		silOSC.free ;

		// two array to collect onsets and silences
		onsets = [];
		silences = [];
	}

	process {
		{processbutton.value = 1}.defer;//on
		{ this.end() }.defer( bufflength ); // auto STOP on timeout

		onsets = [];
		silences = [];
		recbuf.zero; // erase buffer

		recsynth = Synth(\tx_recBuf, [\in, params.in, \bufnum, recbuf.bufnum]);
		sttime = thisThread.seconds ; // start time
		processflag = true;
	}


	end {
		var destpath = sndpath ++ namefield.value ++ "/";
		if ( PathName.new(destpath).isFolder.not, { destpath.mkdir() } );

		recbuf.plot;

		silences.do({arg silence, index; // better loop silences in case there is an attack that hasnt been closed properly at the end
			var sttime, endtime, length, tmpbuffer, filename;

			filename = "plank"++recthis[0].asString++recthis[1].asString++index.asString++".wav";
			sttime = (onsets[index] - attacktime) * recbuf.sampleRate; // -attacktime to recover chopped attacks
			endtime = silence * recbuf.sampleRate;
			length = endtime - sttime;
			tmpbuffer = Buffer.alloc(server, length, 1);
			recbuf.copyData(tmpbuffer, srcStartAt:sttime, numSamples:length);
			tmpbuffer.normalize();
			tmpbuffer.write( (destpath ++ filename), "wav", 'int16' );
		});

		["detected onsets", onsets.size].postln;
		["detected silences", silences.size].postln;

		recthis = nil;
		numhits.string = "0";
		["DONE PROCESSING"].postln;
		processflag = false;

		{processbutton.value = 0}.defer;//off
	}
}
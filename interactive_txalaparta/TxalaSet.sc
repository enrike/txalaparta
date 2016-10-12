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


	*new { | server, sndpath |
		^super.new.initTxalaSet(server, sndpath);
	}

	initTxalaSet { |aserver, asndpath|

		server = aserver;
		sndpath = asndpath;

		if (win.isNil.not, {win.close});

		planksamplebuttons =  Array.fillND([numplanks, plankresolution], { nil });

		names = ["A","B","C","D","E","F","G","H"];
		gridloc = Point.new(60,100);

		respOSC.free ;
		silOSC.free ;

		recbuf = Buffer.alloc(server, 44100 * bufflength, 1); // mono buffer

		// just dumps the sound in signal into the bufnum buffer for later processing at end()
		SynthDef(\tx_recBuf,{ arg in=0, bufnum=0;
			RecordBuf.ar(SoundIn.ar(in), bufnum);
		}).add;

		// look for onsets
		SynthDef(\tx_onset_listener, { arg in=0, thresh = 0.2, relaxtime = 1;
			var sig = SoundIn.ar(in);
			var loc = LocalBuf(1024, 1) ;
			var chain = FFT(loc, sig);
			SendTrig.kr(Onsets.kr(chain, thresh, relaxtime:relaxtime), 999, Loudness.kr(chain));
		}).add ;

		// detect silences
		SynthDef(\tx_silence_detection, { arg in=0, amp=0.005;
			SendTrig.kr(A2K.kr(DetectSilence.ar(SoundIn.ar(in), amp:amp)), 111, 0);
		}).add ;

		this.doGUI();

		{
			// listens for hits. wait until synths are ready
			onsetsynth = Synth(\tx_onset_listener, [\in, ~listenparemeters.in,\threshold, ~listenparemeters.onset.threshold]) ;
			silencesynth = Synth.newPaused(\tx_silence_detection, [\in, ~listenparemeters.in, \amp, ~listenparemeters.tempo.threshold]);
			// two responders
			respOSC = OSCFunc({ arg msg, time; //ATTACK
				if (msg[2] == 999){
					if (processflag, { onsets = onsets.add(time-sttime) }) ;
					("attack detected").postln;
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
					("silence detected").postln ;
					onsetsynth.run ; // now look for begging of a new hit
					silencesynth.run(false) ;
				}
			},'/tr', Server.local.addr);
		}.defer(1);
	}



	doGUI {

		win = Window.new("Plank set manager", Rect(10, 100, 220, 260));
		win.onClose_({
			var destpath, filename, data;
			this.clean();
			respOSC.free;
			silOSC.free;
			onsetsynth.free;
			silencesynth.free;
			recsynth.free;
		});

		StaticText(win, Rect(gridloc.x-50, gridloc.y-25, 50, 25)).string = "Locs -->";
		numplanks.do({arg indexA;

			plankresolution.do({arg indexB;
				var name = (indexA+1).asString++names[indexB];
				if (indexA==0, {
					StaticText(win, Rect(gridloc.x+10+(indexB*30), gridloc.y-25, 50, 25)).string = names[indexB];
				});
				StaticText(win, Rect(gridloc.x-50, (indexA*25)+gridloc.y, 50, 25)).string = "Plank"+(indexA+1).asString;

				planksamplebuttons[indexA][indexB] = Button(win, Rect((30*indexB)+gridloc.x, (indexA*25)+gridloc.y, 30, 25))
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


		StaticText(win, Rect(10, 42, 100, 25)).string = "set name";

		namefield = TextField(win, Rect(75, 42, 140, 25)).value = Date.getDate.stamp;

		Button(win, Rect(10,10, 40, 25))
		.states_([
			["reset", Color.white, Color.black]
		])
		.action_({ arg butt;
			planksamplebuttons.flat.do({ arg but;
				but.states = [[but.states[0][0], Color.white, Color.black], [but.states[1][0], Color.black, Color.red]]
			})
		});

		Button(win, Rect(50,10, 40, 25))
		.states_([
			["HELP", Color.white, Color.black]
		])
		.action_({ arg butt;
			var ww;
			ww = Window.new("Help", Rect(0, 0, 305, 335));
			StaticText(ww, Rect(10, 10, 290, 335)).string = "Each row represents a plank (1,2,3,4,5,6).
Each button in the row represents a position within each plank (A,B,C,D,E).
Ideally those positions go left to right from the edge until the center of each plank.
Give a name to the set before recording the sounds or the set will be named after the date and time.
Select one of the positions (eg 1A) by pressing the corresponding button, then you have 10 secs to hit several times in the same plank location with different amplitudes (low to high). Make sure you leave time for each hit's tile to finish before hitting again. Repeat this procedure for each of the positions in each of the planks. You dont have to fill the five positions, in fact one position per plank is enough, but the more hits the richer the output of the Interactive txalaparta will sound.
After 10 secs the program will process the recordings and try to detect, cut, normalise and save each of the hits into a separated file. It is not a bad idea to open the files in a sound editor (eg. Audacity) to see if they are correct, the system is not perfect!";
			ww.front
		});

		Button( win, Rect(90,10,40,25)) //Rect(140,30,70,25))
		.states_([
			["meter", Color.white, Color.black],
		])
		.action_({ arg but;
			server.meter(1,1);
		});

		processbutton = Button(win, Rect(142,10, 57, 25))
		.states_([
			["procesing", Color.white, Color.grey],
			["procesing", Color.red, Color.grey],
			["procesing", Color.white, Color.red]
		]);

		numhits = StaticText(win, Rect(205, 10, 30, 25)).string = "0";

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

		recsynth = Synth(\tx_recBuf, [\in, ~listenparemeters.in, \bufnum, recbuf.bufnum]);
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
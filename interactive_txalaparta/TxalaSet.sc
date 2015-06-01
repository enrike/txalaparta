// license GPL
// by www.ixi-audio.net

/*
TxalaSet.new(s, thisProcess.nowExecutingPath.dirname++"/sounds/")
*/



TxalaSet{

	var attacktime = 0.01; // THIS IS A SAFE GAP ARTIFICIALLY ENTERED TO AVOID CHOPPING THE START OF THE SOUND
	var numplanks = 6; // max planks
	var plankresolution = 5; // max positions per plank 0-4
	var ampresolution = 5; // max amps per position 0-4
	var bufflength = 10; // secs
	var planksamplebuttons;// Array.fillND([numplanks, plankresolution], { nil });
	var names;// = ["A","B","C","D","E","F","G","H"];
	var gridloc;// = Point.new(50,50);
	var recsynth;
	var sndpath;// = thisProcess.nowExecutingPath.dirname++"/sounds/";
	var silentchannel=100; // channel to send/listen sound while processing
	var processbufferF;
	var endF;
	var sttime;
	var processbutton;
	var recbuf;
	var win, scope, namefield, namefieldstr, numhits;
	var server, onsetsynth, silencesynth;
	var respOSC, silOSC;
	var onsets, silences;


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

		SynthDef(\recBuf,{ arg in=0, bufnum=0;
			RecordBuf.ar(SoundIn.ar(in), bufnum);
		}).add;

		// look for onsets
		SynthDef(\listener, { arg in=0, thresh = 0.2, relaxtime = 1;
			var sig = SoundIn.ar(in);
			var loc = LocalBuf(1024, 1) ;
			var chain = FFT(loc, sig);
			SendTrig.kr(Onsets.kr(chain, thresh, relaxtime:relaxtime), 999, Loudness.kr(chain));
		}).add ;

		// detect silences
		SynthDef(\silence, { arg in=0, amp=0.005;
			SendTrig.kr(A2K.kr(DetectSilence.ar(SoundIn.ar(in), amp:amp)), 111, 0);
		}).add ;

		this.doGUI();
	}



	doGUI {

		win = Window.new("", Rect(10, 100, 220, 260));
		win.onClose_({
			var destpath, filename, data;

			respOSC.free ;
			silOSC.free ;
			onsetsynth.free;
			silencesynth.free;
			recsynth.free;

			// save a file with the data from the chromagram into the directory with the new samples
			destpath = sndpath++namefieldstr++"/chromagram.preset";

			destpath.postln;

			data = Dictionary.new;
			try {
				data.put(\plankdata, ~plankdata);
			 	data.writeArchive(destpath);
			}{|error|
				("did not create a new sample set").postln;
			};

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
						~recindex = [indexA, indexB];
						~plankdata[indexA][indexB] = []; // CLEAR THIS SLOT. to avoid appending more and more...
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
		namefieldstr = namefield.value; // this is required later when the window is closing.

		Button(win, Rect(10,10, 50, 25))
		.states_([
			["reset", Color.white, Color.black]
		])
		.action_({ arg butt;
			planksamplebuttons.flat.do({ arg but;
				but.states = [[but.states[0][0], Color.white, Color.black], [but.states[1][0], Color.black, Color.red]]
			})
		});

		Button(win, Rect(60,10, 50, 25))
		.states_([
			["HELP", Color.white, Color.black]
		])
		.action_({ arg butt;
			var ww;
			ww = Window.new("Help", Rect(0, 0, 300, 200));
			StaticText(ww, Rect(10, 10, 290, 150)).string = "Each row represents a plank. Each button in the row is a position in the plank. Ideally left to right from the edge to the center. Select one of the positions by pressing the button and you have 10 secs to hit several times in the same plank location. On timeout the program processes the recording and tries to save each of the hits to a separated file. Repeat this for each of the positions in each of the planks. You dont have to fill all positions, one per plank is enough but the more the richer it will sound";
			ww.front
		});

		// DetectSilence controls //
		processbutton = Button(win, Rect(110,10, 70, 25))
		.states_([
			["procesing", Color.white, Color.black],
			["procesing", Color.white, Color.red]
		]);

		numhits = StaticText(win, Rect(190, 10, 30, 25)).string = "0";

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
		{processbutton.value = 1}.defer;
		{ this.end() }.defer( bufflength ); // STOP later

		this.clean(); // just in case
		recbuf.zero; // erase buffer

		onsetsynth = Synth(\listener, [\in, ~listenparemeters.in]) ;
		silencesynth = Synth.newPaused(\silence, [\in, ~listenparemeters.in]);

		recsynth = Synth(\recBuf, [\in, ~listenparemeters.in, \bufnum, recbuf.bufnum]);
		sttime = thisThread.seconds ; // start time

		// two responders
		respOSC = OSCFunc({ arg msg, time;
			if (msg[2] == 999){
				onsets = onsets.add(time-sttime) ;
				silencesynth.run ;
				onsetsynth.run(false) ;
				("attack"+(time-sttime)).postln;
			}
		},'/tr', Server.local.addr);

		silOSC = OSCFunc({ arg msg, time;
			if (msg[2] == 111){
				silences = silences.add(time-sttime) ;
				onsetsynth.run ;
				silencesynth.run(false) ;
				("silence"+(time-sttime)).postln ;
				{ numhits.string =  (numhits.string.asInt + 1).asString }.defer;
			}
		},'/tr', Server.local.addr);
	}


	end {
		var destpath;

		recbuf.plot;

		destpath = sndpath++namefield.value++"/";
		namefieldstr = namefield.value; // this is required later when the window is closing.

		silences.do({arg silence, index; // better loop silences in case there is an attack that hasnt been closed properly
			var sttime, endtime, length, tmpbuffer, filename;
			//~recindex.postln;
			filename = "plank"++~recindex[0].asString++~recindex[1].asString++index.asString++".wav";
			//filename.postln;
			sttime = (onsets[index] - attacktime) * recbuf.sampleRate;
			endtime = silence * recbuf.sampleRate;
			length = endtime - sttime;
			tmpbuffer = Buffer.alloc(server, length, 1);
			recbuf.copyData(tmpbuffer, srcStartAt:sttime, numSamples:length);
			tmpbuffer.normalize();

			if ( PathName.new(destpath).isFolder.not, { destpath.mkdir() } );
			tmpbuffer.write( (destpath ++ filename), "wav", 'int16' );
		});

		["onsets",onsets.size].postln;
		["silences",silences.size].postln;

		this.clean();

		~recindex = nil;
		numhits.string = "0";
		["DONE PROCESSING"].postln;

		{processbutton.value = 0}.defer;
	}
}
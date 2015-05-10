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
	var win, scope, namefield, numhits;
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

		//sndpath.postln;
/*		if (~listenparemeters.isNil, {
			~listenparemeters = ().add(\in->0).add(\gain->2.7);
			~listenparemeters.tempo = ().add(\threshold->0.1).add(\falltime->0.24).add(\checkrate->20);
			~listenparemeters.onset = ().add(\threshold->0.93).add(\relaxtime->0.022).add(\floor->0.24).add(\mingap->1);
		});*/

		planksamplebuttons =  Array.fillND([numplanks, plankresolution], { nil });

		names = ["A","B","C","D","E","F","G","H"];
		gridloc = Point.new(50,50);

		respOSC.free ;
		silOSC.free ;

		recbuf = Buffer.alloc(server, 44100 * bufflength, 1); // mono buffer

		SynthDef(\recBuf,{ arg in=0, bufnum=0;
			RecordBuf.ar(SoundIn.ar(in), bufnum);
		}).add;

		// look for onsets
		SynthDef(\listener, { arg in=0, thresh = 0.3, relaxtime = 1;
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

		win = Window.new("", Rect(10, 100, 340, 210));
		win.onClose_({
			//if (scope.isNil.not, {scope.kill }) ;
			respOSC.free ;
			silOSC.free ;
			onsetsynth.free;
			silencesynth.free;
			recsynth.free;
		});

		numplanks.do({arg indexA;

			plankresolution.do({arg indexB;
				var name = (indexA+1).asString++names[indexB];
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

/*						planksamplebuttons.flat.do({arg bu;
							if ( (bu.value==1) && (bu!=butt), { bu.valueAction_(0) }, { bu.value=0 }); // update this***
						});*/

						//butt.value = 1;
						{ butt.valueAction_(0) }.defer(bufflength); //auto go OFF
					}, {
						butt.states = [[name, Color.red, Color.black], [name, Color.black, Color.red]];
						butt.value = 0;
						//recsynth.free;
						//recbuf.plot;
						//this.end(); // correct???
						//this.process();
					})
				});

			});
		});

		//scope = FreqScopeView(win, Rect(200, 50, 100, 50));
		//scope.inBus_(8);
		//scope.active_(true);

		//server.scope(1,8);//bus 25 from the txalaonset synth


/*		scope = ScopeView(win, Rect(200, 50, 100, 50));
		scope.bufnum = 8; // sound in ??
		*/
/*		win.view.decorator = FlowLayout( Rect(200, 50, 100, 50) );
		scope = Stethoscope.new(server, view:win.view);
		*/

		namefield = TextField(win, Rect(210, 42, 120, 25)).value = Date.getDate.stamp;


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
			ww = Window.new("Help", Rect(0, 0, 300, 500));
			StaticText(ww, Rect(5, 5, 295, 495)).string = "each row is a plank. each button in a row is a position in the plank. ideally left to right from the edge to the center. select one of the positions by pressing the button and you have 8 secs to hit up to 5 times the same position. after the 8 secs the program processes the recording and tries to save each of the hits to a separated file. do this for each of the positions in each of the planks";
			ww.front
		});

/*		Button(win, Rect(120,10, 80, 25)) // loads current sample set into memory
		.states_([
			["load", Color.white, Color.black]
		])
		.action_({ arg butt;
			~buffers.do({arg plank, indexplank;
				plank.do({ arg pos, indexpos;
					pos.do({ arg amp, indexamp;
						var filename="plank";
						filename = filename ++ indexplank.asString++indexpos.asString++indexamp.asString++".wav";

						if ( PathName.new(sndpath ++ filename).isFile, {
							~buffers[indexplank][indexpos][indexamp] = Buffer.read(server, sndpath ++ filename);
						})
					})
				})
			});
		});*/


		// DetectSilence controls //
		numhits = StaticText(win, Rect(170, 10, 30, 25)).string = "0";

		processbutton = Button(win, Rect(200,10, 70, 25))
		.states_([
			["procesing", Color.white, Color.black],
			["procesing", Color.white, Color.red]
		]);

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
				("attack"+(time-sttime)).postln;
				onsets = onsets.add(time-sttime) ;
				silencesynth.run ;
				onsetsynth.run(false) ;
			}
		},'/tr', Server.local.addr);

		silOSC = OSCFunc({ arg msg, time;
			if (msg[2] == 111){
				("silence"+(time-sttime)).postln ;
				{ numhits.string =  (numhits.string.asInt + 1).asString }.defer;
				silences = silences.add(time-sttime) ;
				onsetsynth.run ;
				silencesynth.run(false) ;
			}
		},'/tr', Server.local.addr);
	}


	end {
		var destpath;

		recbuf.plot;

		destpath = sndpath++namefield.value++"/";

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
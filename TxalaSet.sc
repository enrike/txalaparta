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
	var bufflength = 8; // secs
	var planksamplebuttons;// Array.fillND([numplanks, plankresolution], { nil });
	var names;// = ["A","B","C","D","E","F","G","H"];
	var gridloc;// = Point.new(50,50);
	var speed = 2.5; // could we speed the processing up???
	var recsynth;
	var sndpath;// = thisProcess.nowExecutingPath.dirname++"/sounds/";
	var silentchannel=100; // channel to send/listen sound while processing
	var processbufferF;
	var endF;
	var sttime;
	var processbutton;
	var recbuf;
	var win, scope, namefield;
	var server, onsetsynth, silencesynth;
	var respOSC, silOSC;
	var onsets, silences;


	*new { | server, sndpath |
		^super.new.initTxalaSet(server, sndpath);
	}

	initTxalaSet { |aserver, asndpath|

		server = aserver;
		sndpath = asndpath;

		//sndpath.postln;

		if (win.isNil.not, {win.close});

		planksamplebuttons =  Array.fillND([numplanks, plankresolution], { nil });

		names = ["A","B","C","D","E","F","G","H"];
		gridloc = Point.new(50,50);

		~buffers = Array.fillND([numplanks, plankresolution, ampresolution], { nil });

		respOSC.free ;
		silOSC.free ;

		recbuf = Buffer.alloc(server, 44100 * bufflength, 1); // mono buffer

		SynthDef(\recBuf,{ arg in=0, bufnum=0;
			RecordBuf.ar(SoundIn.ar(in), bufnum);
		}).load(server);

		// look for onsets
		SynthDef(\listener, { arg in=0, thresh = 0.2; // thres should take values from global parameters
			var sig = SoundIn.ar(in);//In.ar(in) ;
			var loc = LocalBuf(1024, 1) ;
			var chain = FFT(loc, sig);
			SendTrig.kr(Onsets.kr(chain, thresh, relaxtime:0.5), 999, Loudness.kr(chain));
		}).add ;

		// detect silences
		SynthDef(\silence, { arg in=0, amp=(-30.dbamp); //amp should take values from global parameters
			SendTrig.kr(A2K.kr(DetectSilence.ar(SoundIn.ar(in), amp:amp)), 111, 0);
		}).add ;

		this.doGUI();
	}



	doGUI {

		win = Window.new("", Rect(10, 100, 340, 210));
		win.onClose_({
			if (scope.isNil.not, {scope.kill }) ;
			respOSC.free ;
			silOSC.free ;
			onsetsynth.free;
			silencesynth.free
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
						recsynth = Synth(\recBuf, [\bufnum, recbuf.bufnum]);
						this.process(); // Task that processes the sound in realtime

/*						planksamplebuttons.flat.do({arg bu;
							if ( (bu.value==1) && (bu!=butt), { bu.valueAction_(0) }, { bu.value=0 }); // update this***
						});*/

						butt.value = 1;
						{ butt.valueAction_(0) }.defer(bufflength); //auto OFF
					}, {
						//"going OFF".postln;
						butt.states = [[name, Color.red, Color.black], [name, Color.black, Color.red]];
						recsynth.free;
						//recbuf.plot;
						this.end(); // correct???
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

		Button(win, Rect(120,10, 80, 25)) // loads current sample set into memory
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
		});

		processbutton = Button(win, Rect(200,10, 70, 25))
		.states_([
			["procesing", Color.white, Color.black],
			["procesing", Color.white, Color.red]
		]);

		win.front;
	}

	process {

		var func =	Task({

			("PROCESSING BUFFER" + ~recindex).postln;
			// name of file, typically exprtessed as the midi number

			{processbutton.value = 1}.defer;

			{ this.end() }.defer( (recbuf.numFrames / recbuf.numChannels ) / recbuf.sampleRate ); // STOP

			sttime = thisThread.seconds ; // start time
			onsetsynth = Synth(\listener, [\in, 0, \thresh, ~listenparemeters.tempo.threshold]) ;
			silencesynth = Synth.newPaused(\silence, [\in, 0, \amp, ~listenparemeters.onset.threshold]) ;

			// two array to collect onsets and silences
			onsets = [] ;
			silences = [] ;

			// two responders
			respOSC = OSCFunc({ arg msg, time;
				if (msg[2] == 999){
					("attack"+(time-sttime)).postln;
					onsets = onsets.add(( (time-sttime)*speed)) ;
					silencesynth.run ;
					onsetsynth.run(false) ;
				}
			},'/tr', Server.local.addr);

			silOSC = OSCFunc({ arg msg, time;
				if (msg[2] == 111){
					("silence"+(time-sttime)).postln ;
					silences = silences.add(( (time-sttime)*speed )) ;
					onsetsynth.run ;
					silencesynth.run(false) ;
				}
			},'/tr', Server.local.addr);

		});

		func.start; //
	}


	end {
		"DONE PROCESSING".postln;

		recbuf.plot;

		//there is something wrong here. the more hits are detected the more chop they are in the attack.

		onsetsynth.stop();

		onsets.do({arg onset, index;
			var sttime, endtime, length, tmpbuffer, destpath, filename;
			(sndpath++namefield.value++"/").postln;
			destpath = sndpath++namefield.value++"/";
			filename = "plank"++~recindex[0].asString++~recindex[1].asString++index.asString++".wav";
			sttime = (onset - attacktime) * recbuf.sampleRate;
			endtime = silences[index] * recbuf.sampleRate;
			length = endtime - sttime;
			tmpbuffer = Buffer.alloc(server, length, 1);
			recbuf.copyData(tmpbuffer, srcStartAt:sttime, numSamples:length);
			tmpbuffer.normalize();
			// here ease the first and last msecs

			if ( PathName.new(destpath).isFolder.not, { destpath.mkdir() } );
			tmpbuffer.write( (destpath ++ filename), "wav", 'int16' );
		});

/*		~onsets.postln;
		~silences.postln;*/

		respOSC.free ;
		silOSC.free ;

		{processbutton.value = 0}.defer;
	}
}
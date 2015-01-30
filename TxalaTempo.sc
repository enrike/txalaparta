/*
TxalaTempo
listens to incomming signal distinguising silence/signal. Calculates tempo between silence ends. It is thought to calculate the tempo of a single txalaparta player. Works well with tempos up to 140. When silence between hits dissapear it does not work at all.

License GPL.
by www.ixi-audio.net

*/

TxalaTempo {

	var <>bpm = 0, tempocalc, standalone, curPattern, patternsttime;
	var synthtempo, synthonset, synthtempoOSCcb, synthonsetOSCcb, channel, server;
	var win, label, parent, listeningDisplay; // gui

	*new {| parent, server, channel = 0, standalone=true |
		^super.new.initTxalaTempo( parent, server, channel, standalone );
	}

	initTxalaTempo {| aparent, aserver, achannel, astandalone |
		parent = aparent;
		server = aserver;
		standalone = astandalone;
		channel = achannel;

		curPattern = nil;
		patternsttime = 0;

		// silence detector
		// this needs to stop listening when supercollider is playing
		// to avoid listening to itself
		SynthDef(\txalatempo, {|ch=0, amp=0.01, falltime=0.1, checkrate=45 |
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(ch), amp, falltime);
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
		}).store;

		//onset detector
		SynthDef(\txalaonsetlistener, { |ch=0, amp=1, threshold=0.6, relaxtime = 2.1, floor=0.1, mingap=10|
			var fft, onset, in, level=0, freq=0, hasFreq=false;
			in = SoundIn.ar(ch) * amp;
			fft = FFT(LocalBuf(2048), in);
			onset = Onsets.kr(fft, threshold, \rcomplex, relaxtime, floor, mingap, 11, 1, 0);// beat detection
			/*	*kr (chain, threshold: 0.5, odftype: 'rcomplex', relaxtime: 1, floor: 0.1, mingap: 10, medianspan: 11, whtype: 1, rawodf: 0)*/
			level = Amplitude.kr(in);
			# freq, hasFreq = Pitch.kr(in, ampThreshold: 0.02, median: 7);
			SendReply.kr(onset, '/txalaonset', [level, hasFreq, freq]);
		}).store;

		//this.reset();
		this.doGui();
	}

	kill {
		synthtempo.free;
		synthonset.free;
		synthtempoOSCcb.remove;
		synthonsetOSCcb.remove;
	}

	reset {
		this.kill();
		tempocalc = nil;
		tempocalc = TempoCalculator.new(this, 2, 0);
		this.doAudio();
	}
	setCheckRate {arg value;
		synthtempo.set(\checkrate, value);
	}
	setFallTime {arg value;
		synthtempo.set(\falltime, value);
	}
	setAmp {arg value;
		synthtempo.set(\amp, value);
	}

	calculate {arg value;
		bpm = tempocalc.process(value);
		{ label.string = "BPM:" + bpm }.defer;
		^bpm;
	}

	tooglelisten{arg flag; // 0 or amp
		var value;
		//if (flag, {value=})
		["listening to myself?", flag.asInt].postln;
		listeningDisplay.action(flag.asInt);
		synthtempo.set(\amp, flag.asInt);
		synthonset.set(\amp, flag.asInt);
	}

	// this is called by tempocalculator on new pattern detected
	// it allows to grup together hits into patterns.
	// it is triggered when the first hit of a new pattern is detected.
	// that means the previous pattern is finished and can be analysed
	finisholdpattern {
		parent.finisholdpattern(bpm, curPattern);
		curPattern = nil; // because it starts a new one
	}

	doAudio { // synths and OSC responders from synths here //
		synthtempoOSCcb.remove;
		synthonsetOSCcb.remove;
		synthtempo.free;
		synthonset.free;

		synthtempo = Synth(\txalatempo, [\ch, channel]);
		synthtempoOSCcb = OSCFunc({ arg msg, time; // change between signal and silence
			this.calculate(msg[3])
		},'/txalasil', server.addr);

		synthonset = Synth(\txalaonsetlistener);
		synthonsetOSCcb = OSCFunc({ arg msg, time; // new hot detected
			var hit, hittime;

			if (curPattern.isNil, {
				hittime = 0; // start counting on first one
				patternsttime = Main.elapsedTime;
			},{
				hittime = Main.elapsedTime - patternsttime;
			});

			hit = ().add(\time -> hittime)
				.add(\amp -> msg[3])
				.add(\player -> 1) //always 1 in this case
				.add(\plank -> 1);// here needs to match mgs[5] against existing samples freq

			curPattern = curPattern.add(hit);

			//["new hit, curPattern state is",curPattern].postln;

			// now correct the time for the txalascore
			hit.time = Main.elapsedTime - parent.startTime;
			parent.newhit(hit); // now display this hit in txalascore
		},'/txalaonset', server.addr);
	}

	doGui {
		var yloc = 0;

		if (win.isNil.not, {win.close});
		win = Window("tempo detection using silence. for txalaparta",  Rect(0, 0, 350, 350));

		// here it should free all OSC responders with /txalasil address
		win.onClose = {	synthtempo.free };

		label = StaticText(win, Rect(10, 0, 90, 25));
		label.string = "BPM:" + bpm;

		listeningDisplay = Button( win, Rect(10,20,20,20)) // should go off when I am playing myself
		.states_([
			["", Color.white, Color.red]
		]).valueAction_(1);

		Button( win, Rect(208,3,70,25))
		.states_([
			["listen/pause", Color.white, Color.black],
			["listen/pause", Color.black, Color.green],
		])
		.action_({ arg but;
			if (but.value.asBoolean, {
				this.reset()
			},{
				this.kill()
			});
		});

		Button( win, Rect(278,3,70,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg but;
			this.reset();
		});

		Button( win, Rect(208,30,70,25))
		.states_([
			["scope", Color.white, Color.black],
		])
		.action_({ arg but;
			server.scope(1).setProperties(1,8);
		});

		Button( win, Rect(280,30,20,25))
		.states_([
			["V", Color.white, Color.grey],
			["V", Color.white, Color.blue],
			["V", Color.white, Color.green],
			["V", Color.white, Color.red]
		])
		.action_({ arg but;
			tempocalc.verbose = but.value;
		});



		// channel pull down here
		StaticText(win, Rect(110, 0, 50, 25)).string = "channel";
		PopUpMenu(win,Rect(160,3,40,20))
		.items_( ["0","1","2","3","4","5","6","7","8","9","10"]);

		// |ch=0, amp=0.01, falltime=0.1, checkrate=45 |
		StaticText(win, Rect(5, 45, 180, 25)).string = "Tempo detection";
		EZSlider( win,
			Rect(0,yloc+70,350,20),
			"checkrate",
			ControlSpec(1, 350, \lin, 1, 45, "Hz"),
			{ arg ez;
				synthtempo.set(\checkrate, ez.value.asFloat);
			},
			initVal: 45,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+90,350,20),
			"amp",
			ControlSpec(0.01, 3, \lin, 0.01, 0.01, ""),
			{ arg ez;
				synthtempo.set(\amp, ez.value.asFloat);
			},
			initVal: 0.01,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+110,350,20),
			"falltime",
			ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				synthtempo.set(\falltime, ez.value.asFloat);
			},
			initVal: 0.1,
			labelWidth: 60;
		);

		// Onset detection. counts the beats and stores its characteristics
		//|ch=0, threshold=0.6, relaxtime = 2.1, floor=0.1, mingap=10|
		StaticText(win, Rect(5, 145, 180, 25)).string = "Pattern detection";
		EZSlider( win,
			Rect(0,yloc+170,350,20),
			"threshold",
			ControlSpec(0, 1, \lin, 0.01, 0.2, ""),
			{ arg ez;
				synthonset.set(\threshold, ez.value.asFloat);
			},
			initVal: 0.2,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+190,350,20),
			"relaxtime",
			ControlSpec(0.01, 4, \lin, 0.01, 2.1, "ms"),
			{ arg ez;
				synthonset.set(\relaxtime, ez.value.asFloat);
			},
			initVal: 2.1,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+210,350,20),
			"floor",
			ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				synthonset.set(\floor, ez.value.asFloat);
			},
			initVal: 0.1,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+230,350,20),
			"mingap",
			ControlSpec(0.1, 20, \lin, 0.1, 10, "Ms"),
			{ arg ez;
				synthonset.set(\mingap, ez.value.asFloat);
			},
			initVal: 10,
			labelWidth: 60;
		);
		StaticText(win, Rect(5, yloc+260, 280, 25)).string = "Some short of visualization goes here";

		win.front;
	}
}

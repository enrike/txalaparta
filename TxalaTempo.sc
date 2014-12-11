/*
TxalaTempo
listens to incomming signal distinguising silence/signal. Calculates tempo between silence ends. It is thought to calculate the tempo of a single txalaparta player. Works well with tempos up to 140. When silence between hits dissapear it does not work at all.

License GPL.
by www.ixi-audio.net

Usage as standalone panel:
t = TxalaTempo.new(parent, s, 0, true);
t.bpm.postln;
t.setCheckRate(40)
t.setFallTime(0.1)
t.setAmp(0.3)

Usage from an app:
t = TxalaTempo.new(s, 0, false);
//collect its output in a function like this
f = OSCFunc({ arg msg, time;
	msg.postln;
},'/txalasil', s.addr);

*/

TxalaTempo {

	var <>bpm = 0, tempocalc, standalone, dotanim, curPattern, patternsttime;
	var synthtempo, synthonset, channel, server;
	var win, label, parent; // gui

	*new {| parent, server, channel = 0, standalone=true |
		^super.new.initTxalaTempo( parent, server, channel, standalone );
	}

	initTxalaTempo {| aparent, aserver, achannel, astandalone |
		parent = aparent;
		server = aserver;
		standalone = astandalone;
		channel = achannel;
		dotanim = '';

		curPattern = nil;
		patternsttime = 0;

		// silence detector
		SynthDef(\txalatempo, {|ch=0, amp=0.01, falltime=0.1, checkrate=45 |
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(ch), amp, falltime);
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
		}).store;

		//onset detector
		SynthDef(\txalaonsetlistener, { |threshold=0.6, relaxtime = 2.1, floor=0.1, mingap=10|
			var fft, onset, in, amp=0, freq=0, hasFreq=false;
			in = SoundIn.ar(0);
			fft = FFT(Buffer.alloc(server, 512), in);
			onset = Onsets.kr(fft, threshold, \rcomplex, relaxtime, floor, mingap, 11, 1, 0);// beat detection
			/*	*kr (chain, threshold: 0.5, odftype: 'rcomplex', relaxtime: 1, floor: 0.1, mingap: 10, medianspan: 11, whtype: 1, rawodf: 0)*/
			amp = Amplitude.kr(in);
			# freq, hasFreq = Pitch.kr(in, ampThreshold: 0.02, median: 7);
			SendReply.kr(onset, '/txalaonset', [amp, hasFreq, freq]);
		}).store;

		this.reset();
	}

	reset {
		tempocalc = TempoCalculator.new(this, 2, 0);
		this.doAudio();
		this.doGui();
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
		dotanim = dotanim++"."; // just to know its alive
		if (dotanim.size > 4, {dotanim = ''});
		{ label.string = "BPM:" + bpm + dotanim }.defer;
		^bpm;
	}

	// this is called by tempocalculator on new pattern detected
	// it allows to grup together hits into patterns.
	// it is triggered when the first hit of a new pattern is detected.
	// that means the previous pattern is finished and can be analysed
	finisholdpattern {
		parent.finisholdpattern(bpm, curPattern);
		curPattern = nil; // because it starts a new one
	}

	doAudio {
		synthtempo = Synth(\txalatempo, [\ch, channel]);
		OSCFunc({ arg msg, time;
			this.calculate(msg[3])
		},'/txalasil', server.addr);

		synthonset = Synth(\txalaonsetlistener);
		OSCFunc({ arg msg, time;
			var hit, hittime;

			//[curPattern.isNil, Main.elapsedTime - patternsttime].postln;

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

			// now correct the time for the txalascore
			hit.time = Main.elapsedTime - parent.startTime;
			parent.newhit(hit); // now display this hit in txalascore
		},'/txalaonset', server.addr);
	}

	doGui {
		if (win.isNil.not, {win.close});
		win = Window("tempo detection using silence. for txalaparta",  Rect(0, 0, 350, 110));

		// here it should free all OSC responders with /txalasil address
		win.onClose = {	synthtempo.free };

		label = StaticText(win, Rect(10, 0, 90, 25));
		label.string = "BPM:" + bpm;

		Button( win, Rect(115,3,70,25))
		.states_([
			["scope", Color.white, Color.black],
		])
		.action_({ arg but;
			server.scope(1).setProperties(1,8);
		});

		Button( win, Rect(188,3,20,25))
		.states_([
			["V", Color.white, Color.grey],
			["V", Color.white, Color.blue],
			["V", Color.white, Color.green],
			["V", Color.white, Color.red]
		])
		.action_({ arg but;
			tempocalc.verbose = but.value;
		});


		Button( win, Rect(208,3,70,25))
		.states_([
			["play/pause", Color.white, Color.black],
			["play/pause", Color.black, Color.green],
		])
		.action_({ arg but;
			synthtempo.set(\ch, but.value-1);//this needs a simple way to stop listening
		});

		Button( win, Rect(278,3,70,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg but;
			this.reset();
		});

		EZSlider( win,
			Rect(0,30,350,20),
			"checkrate",
			ControlSpec(1, 350, \lin, 1, 45, "Hz"),
			{ arg ez;
				synthtempo.set(\checkrate, ez.value.asFloat);
			},
			initVal: 45,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,55,350,20),
			"amp",
			ControlSpec(0.01, 3, \lin, 0.01, 0.01, ""),
			{ arg ez;
				synthtempo.set(\amp, ez.value.asFloat);
			},
			initVal: 0.01,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,80,350,20),
			"falltime",
			ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				synthtempo.set(\falltime, ez.value.asFloat);
			},
			initVal: 0.1,
			labelWidth: 60;
		);

		win.front;
	}
}

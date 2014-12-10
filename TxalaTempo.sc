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

	var <>bpm = 0, tempocalc, standalone, dotanim;
	var synth, channel, server;
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

		SynthDef(\txalatempo, {|ch=0, amp=0.01, falltime=0.1, checkrate=45 |
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(ch), amp, falltime);
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
		}).store;

		this.reset();
	}

	reset {
		tempocalc = TempoCalculator.new(this, 2, 0);
		this.doAudio();
		this.doGui();
	}

	setCheckRate {arg value;
		synth.set(\checkrate, value);
	}
	setFallTime {arg value;
		synth.set(\falltime, value);
	}
	setAmp {arg value;
		synth.set(\amp, value);
	}

	calculate {arg value;
		bpm = tempocalc.process(value);
		dotanim = dotanim++"."; // just to know its alive
		if (dotanim.size > 4, {dotanim = ''});
		{label.string = "BPM:" + bpm + dotanim}.defer;
		^bpm;
	}

	newhit { // this needs to tell the txalaparta instance that a new hit arrived
		parent.newhit(bpm);
	}

	doAudio {
		synth = Synth(\txalatempo, [\ch, channel]);
		if (standalone, {
			OSCFunc({ arg msg, time;
				this.calculate(msg[3])
			},'/txalasil', server.addr);
		});
	}

	doGui {
		if (win.isNil.not, {win.close});
		win = Window("tempo detection using silence. for txalaparta",  Rect(0, 0, 350, 110));

		// here it should free all OSC responders with /txalasil address
		win.onClose = {	synth.free };

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
			synth.set(\ch, but.value-1);//this needs a simple way to stop listening
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
				synth.set(\checkrate, ez.value.asFloat);
			},
			initVal: 45,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,55,350,20),
			"amp",
			ControlSpec(0.01, 3, \lin, 0.01, 0.01, ""),
			{ arg ez;
				synth.set(\amp, ez.value.asFloat);
			},
			initVal: 0.01,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,80,350,20),
			"falltime",
			ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				synth.set(\falltime, ez.value.asFloat);
			},
			initVal: 0.1,
			labelWidth: 60;
		);

		win.front;
	}
}

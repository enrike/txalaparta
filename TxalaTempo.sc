/*
TxalaTempo
listens to incomming signal distinguising silence/signal. Calculates tempo between silence ends. It is thought to calculate the tempo of a single txalaparta player. Works well with tempos up to 140. When silence between hits dissapear it does not work at all.

License GPL.
by www.ixi-audio.net

Usage:
t = TxalaTempo.new(s, 0);
t.bpm.postln;
t.setCheckRate(40)
t.setFallTime(0.1)
t.setAmp(0.3)


to trap the event from somewhere else:

t = TxalaTempo.new(s, 0, false);
//collect its output this way
f = OSCFunc({ arg msg, time;
	msg.postln;
},'/txalasil', s.addr);

*/

TxalaTempo {

	var <>bpm = 0, tempocalc;
	var synth, channel, server;
	var win, label; // gui

	*new {| server, channel = 0, standalone=true |
		^super.new.initTxalaTempo( server, channel, standalone );
	}

	initTxalaTempo {| aserver, achannel, standalone |
		server = aserver;
		channel = achannel;
		this.reset(standalone);
	}

	reset { arg standalone;
		tempocalc = TempoCalculator.new(2, 1);
		this.doAudio(standalone);
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
		{label.string = "BPM:"+bpm}.defer;

		^bpm;
	}

	doAudio { arg standalone;
		SynthDef(\txalatempo, {|ch=0, amp=0.01, falltime=0.1, checkrate=45 |
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(ch), amp, falltime);
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // this will be collected somewhere else
		}).store;

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
		win.onClose = {
			// free all OSC responders with /txalasil address
			synth.free;
		};

		label = StaticText(win, Rect(60, 0, 90, 25));
		label.string = "BPM:"+bpm;

		Button( win, Rect(248,3,100,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg butt;
			this.reset();
		});

		Button( win, Rect(148,3,100,25))
		.states_([
			["scope", Color.white, Color.black],
		])
		.action_({ arg butt;
			server.scope(1).setProperties(1,8);
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

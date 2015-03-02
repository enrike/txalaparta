/*
s.boot
TxalaTempoDetector.new(nil, s, 0)
*/

TxalaTempoDetector{
/*	var <>bpm = 0, tempocalc, standalone, curPattern, patternsttime;
	var synthtempo, synthonset, synthtempoOSCcb, synthonsetOSCcb, channel, server;
	var win, label, parent, listeningDisplay; // gui*/

	var lasthits, plank, intermakilagap, beatdata;
	//var updatematrixF, resetF, loopF, doGUI;
	var synthtempo, synthtempoOSCcb, process, hitflag = false, calculateTempo, answer, lastpatternsttime, bpms, sanityCheck;
	var hutsunetimeout = 0, grouplength = 0, nowanswering = false, compass, label;


	*new { | parent, server, channel = 0 |
		^super.new.initTxalaTempo( parent, server, channel );
	}


	initTxalaTempo { | aparent, aserver, achannel |

		this.reset();

		SynthDef(\txalatempo, { |in=0, thres=0.1, falltime=0.1, checkrate=30 |
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(in), thres, falltime );
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
		}).add;

		SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
			Out.ar(outbus, amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2)
		}).add;

		synthtempo = Synth(\txalatempo, [\in, achannel]);
		synthtempoOSCcb = OSCFunc({ arg msg, time; // change between signal and silence
			if ( nowanswering.not, { this.process(msg[3]) });
		},'/txalasil', aserver.addr);

		this.doGUI();
	}


	doGUI {
		var win;
		win = Window("tempo detection using silence. for txalaparta",  Rect(10, 50, 350, 100));
		// here it should free all OSC responders with /txalasil address
		win.onClose = {	synthtempo.free };

		label = StaticText(win, Rect(10, 0, 90, 25));
		label.string = "BPM:" + ~bpm;

		Button( win, Rect(108,3,70,25))
		.states_([
			["answer", Color.white, Color.black],
			["answer", Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		Button( win, Rect(198,3,70,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg but;
			this.resetF();
		});

		EZSlider( win,
			Rect(0,35,350,20),
			"threshold",
			ControlSpec(0.01, 1.5, \lin, 0.01, 0.1, ""),
			{ arg ez;
				//synthtempo.set(\amp, ez.value.asFloat);
				synthtempo.free; // supercollider does not allow to update the amp parameter on the fly
				synthtempo = Synth(\txalatempo, [\thres, ez.value.asFloat]);
			},
			initVal: 0.1,
			labelWidth: 60;
		);
		win.front;
	}



	process {arg value;
		var timetogo, gap;
		if (value == 0, { // signal
			if (hitflag.not, { // group just started
				hitflag = true;
				compass = compass + 1;
				grouplength = Main.elapsedTime;
				~bpm = this.calculateTempo();
				hutsunetimeout = lastpatternsttime + (60/~bpm) + ((60/~bpm)/3.2); // next expected hit should go before that
				("~~~~~~~~~~~~~~~~~~~~~~~~~~~~"+compass).postln;
			});
			"---------------------".postln;
		}, { // silence
			if (hitflag, { // group just ended
				hitflag = false;
				grouplength = Main.elapsedTime - grouplength;
				if(~answer, { this.answer() }); // schedule here the answer time acording to bpm
			}, {
				if ( hutsunetimeout.isNil.not, {
					if (Main.elapsedTime > hutsunetimeout, {
						"[[[[[[[ hutsune ]]]]]]]]".postln;
						lasthits[1] = 0; // update mkv
						lastpatternsttime = lastpatternsttime + (60/~bpm); // must update lastpasttensttime otherwise tempo drops
						hutsunetimeout = nil;
						if(~answer, { this.answer() });
					});
				}, { // if too long after the last signal wa received reset me
					if ((Main.elapsedTime > (lastpatternsttime + 3)), {
							this.resetF();
					});
				});
				("." + ~bpm).postln;
			});
		});
	}


	sanityCheck {arg abpm;
		if (abpm == inf, {
			abpm = bpms.last;
			"inf!!!!!".postln;
		});
		if (abpm > 250, { abpm = bpms.last}); // if too high something went wrong

		^abpm;
	}

	calculateTempo  {
		var newTempo, nowTime;

		nowTime =  Main.elapsedTime;
		newTempo = (60/(nowTime - lastpatternsttime)).round(0.1);
		newTempo = this.sanityCheck(newTempo);
		bpms = bpms.shift(1, newTempo); // store
		newTempo = (bpms.sum/bpms.size); // average of all stored tempos

		{label.string = "BPM:" + newTempo}.defer; // GUI. display value

		lastpatternsttime = nowTime; // update

		^newTempo;
	}

	answer  {
		var weights, curhits, gap, halfcompass, timetogo=0;

		"SCHEDULE ANSWER".postln;

		weights = ~beatweigths[ lasthits[1] ];
		curhits = [0,1,2,3,4].wchoose(weights.normalizeSum);
		lasthits[1] = curhits; // update me

		// must calculate when in the future needs to happen this group
		halfcompass = (60/~bpm/2);

		timetogo = lastpatternsttime + halfcompass - Main.elapsedTime;

		if (curhits > 0, {
			gap = (halfcompass * ~spread) / curhits;
		},{
			gap = 0;
		});

		[curhits, gap, halfcompass].postln;

		curhits.do({arg index;
			var playtime = timetogo + (gap * index) + rrand(~intermakilaswing.neg, ~intermakilaswing);

			if ( playtime.isNaN, { playtime = 0 } );
			if ( playtime == inf, { playtime = 0 } );

			{
				if (index==0, { nowanswering = true });
				if ((index==(curhits-1)), { {nowanswering = false}.defer(0.25) }); // when the last hit stops
				Synth(\playBuf, [\amp, (~volume+rrand(-0.05, 0.05)), \freq, (1+rrand(-0.003, 0.003)), \bufnum, plank.bufnum]);
				("+++++++++++++++++++++++++++"+compass+index).postln
			}.defer(playtime);
		});
	}
}
// txalaparta-like markov chain system
// license GPL
// by www.ixi-audio.net

/*
(
s.boot;
s.waitForBoot{
   t = TxalaMarkovTempo.new(s)
}
)
*/

/* detection:
+ tempo: DetectSilence
group of hits start point
grup of hits end point
- calculate the bpm when a group starts by measuring the distance from the previous group
- trigger the answer when the group finish
- check for hutsunes and answer to those

+ number of hits per group: OnSets
- distance between hits
- amplitude of each hit
- pitch of each hit (to do)
*/

TxalaMarkovTempo{

	var loopF, plank, intermakilagap, server;
	var doGUI, label, reset, answer, hutsune;
	var txalasilence, txalaonset, markov, lastPattern;
	var txalascoreGUI;

	*new {| aserver |
		^super.new.initTxalaMarkovTempo(aserver);
	}

	initTxalaMarkovTempo { arg aserver;
		server = aserver;
		this.init();
		this.reset();
		this.doGUI();
	}

	init {
		~bpm = 60;
		~volume = 0.6;
		~answer = false;
		~answermode = 0; //0,1,2,3: imitation, random (with GUI parameters), markov1, markov2
		~answertimecorrection = 6;

		~swing = 0.1;
		~intermakilaswing = 0.01;
		~spread = 0.7;

		// this is to keep all the values of the listening synths in one place
		~listenparemeters = ().add(\in->0).add(\amp->1);
		~listenparemeters.tempo = ().add(\threshold->0.5).add(\falltime->0.1).add(\checkrate->20);
		~listenparemeters.onset = ().add(\threshold->0.6).add(\relaxtime->2.1).add(\floor->0.1).add(\mingap->0.1);

		lastPattern = nil;

		this.reset();

		plank = Buffer.read(server, "./sounds/00_ugarte3.wav"); // TO DO: transfer to higher level
	}

	// SYNTH'S CALLBACKS /////////////////////////////////////////////////////////////////
	hutsune {
		lastPattern = nil;
	}

	loop {
		{ label.string = "BPM" + ~bpm + "\nCompass" + txalasilence.compass}.defer
	}

	broadcastgroupended { // silence detection calls this.
		lastPattern = txalaonset.closegroup(); // to close onset detector
	}

	newgroup {
		//txalasilence.groupstart();
	}

	newonset { arg hittime, amp, player, freq;
		txalascoreGUI.hit(hittime, amp, player, plank);
	}
	/////////////////////////////////////////////////////////////////////////////

	reset  {
		"+++++++++ RESET ++++++++++++++++++++++++++++++++++++++++++++++++++++++++".postln;

		try {
			txalasilence.kill();
			txalaonset.kill();
		} {|error|

		};

		txalasilence = TxalaSilenceDetection.new(this, server, true); // parent, server, mode, answermode
		txalaonset= TxalaOnsetDetection.new(this, server);
		markov = TxalaMarkov.new;
		txalascoreGUI = TxalaScoreGUI.new;
	}


	doGUI  {
		var win, yloc = 35, gap=20;
		win = Window("tempo detection using silence. for txalaparta",  Rect(10, 50, 355, 300));
		win.onClose = {
			txalasilence.kill();
			txalaonset.kill();
		};

		label = StaticText(win, Rect(10, 3, 130, 25));
		label.string = "BPM:" + ~bpm;

		// row of buttons on top side
		Button( win, Rect(140,3,70,25))
		.states_([
			["answer", Color.white, Color.black],
			["answer", Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		Button( win, Rect(210,3,70,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg but;
			this.reset();
		});

		Button( win, Rect(285,3,70,25)) //Rect(140,30,70,25))
		.states_([
			["scope in", Color.white, Color.black],
		])
		.action_({ arg but;
			server.scope(1,8);
		});

		// mode menu
		StaticText(win, Rect(10, yloc-3, 100, 25)).string = "Answer mode";
		PopUpMenu(win,Rect(100,yloc, 110,20))
		.items_(["imitation", "markov", "markov learning"])
		.action_({ arg menu;
		    ~answermode = menu.value.asInt;
			("changing to answer mode:" + menu.item).postln;
		})
		.valueAction_(~answermode);

		Button( win, Rect(220,yloc-5,70,25))
		.states_([
			["score", Color.white, Color.black],
			["score", Color.black, Color.green],
		])
		.action_({ arg but;
			txalascoreGUI.doTxalaScore()
		});

		// answer time correction
		EZSlider( win,
			Rect(0,yloc+(gap),350,20),
			"time correction",
			ControlSpec(1, 10, \lin, 0.01, 6, ""),
			{ arg ez;
				~answertimecorrection = ez.value.asFloat;
			},
			initVal: ~answertimecorrection,
			labelWidth: 60;
		);

		// amplitudes
		// incomming amp correction
		EZSlider( win,
			Rect(0,yloc+(gap*2),350,20),
			"in amp",
			ControlSpec(0, 2, \lin, 0.01, 1, ""),
			{ arg ez;
				txalaonset.synth.set(\amp, ez.value.asFloat);
				txalasilence.synth.set(\amp, ez.value.asFloat);
				~listenparemeters.amp = ez.value.asFloat;
			},
			initVal: ~listenparemeters.amp,
			labelWidth: 60;
		);
		// ~amplitude
		EZSlider( win,
			Rect(0,yloc+(gap*3),350,20),
			"out amp",
			ControlSpec(0, 1, \lin, 0.01, 1, ""),
			{ arg ez;
				~volume = ez.value.asFloat;
			},
			initVal: ~volume,
			labelWidth: 60;
		);


		// DetectSilence controls //
		StaticText(win, Rect(5, yloc+(gap*4), 180, 25)).string = "Tempo detection";

		EZSlider( win,
			Rect(0,yloc+(gap*5),350,20),
			"threshold",
			ControlSpec(0.01, 1.5, \lin, 0.01, 0.2, ""),
			{ arg ez;
				txalasilence.updatethreshold(ez.value.asFloat); // this is different because a bug? in supercollider
				~listenparemeters.tempo.threshold = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.threshold,
			labelWidth: 60;
		).valueAction_(~listenparemeters.tempo.threshold);

		EZSlider( win,
			Rect(0,yloc+(gap*6),350,20),
			"falltime",
			ControlSpec(0.01, 20, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				//txalasilence.updatefalltime(ez.value.asFloat);
				txalasilence.synth.set(\falltime, ez.value.asFloat);
				~listenparemeters.tempo.falltime = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.falltime,
			labelWidth: 60;
		);

		EZSlider( win,
			Rect(0,yloc+(gap*7),350,20),
			"rate",
			ControlSpec(5, 60, \lin, 1, 30, ""),
			{ arg ez;
				txalasilence.synth.set(\checkrate, ez.value.asFloat);
				~listenparemeters.tempo.checkrate = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.checkrate,
			labelWidth: 60;
		);

		// Onset pattern detection controls //
		StaticText(win, Rect(5, yloc+(gap*8), 180, 25)).string = "Hit onset detection";

		EZSlider( win,
			Rect(0,yloc+(gap*9),350,20),
			"threshold",
			ControlSpec(0, 1, \lin, 0.01, 0.4, ""),
			{ arg ez;
				txalaonset.synth.set(\threshold, ez.value.asFloat);
				~listenparemeters.onset.threshold = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.threshold,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+(gap*10),350,20),
			"relaxtime",
			ControlSpec(0.01, 4, \lin, 0.01, 2.1, "ms"),
			{ arg ez;
				txalaonset.synth.set(\relaxtime, ez.value.asFloat);
				~listenparemeters.onset.relaxtime = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.relaxtime,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+(gap*11),350,20),
			"floor",
			ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				txalaonset.synth.set(\floor, ez.value.asFloat);
				~listenparemeters.onset.floor = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.floor,
			labelWidth: 60;
		);
		EZSlider( win,
			Rect(0,yloc+(gap*12),350,20),
			"mingap",
			ControlSpec(0.1, 20, \lin, 0.1, 0.1, "Ms"),
			{ arg ez;
				txalaonset.synth.set(\mingap, ez.value.asFloat);
				~listenparemeters.onset.mingap = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.mingap,
			labelWidth: 60;
		);

		win.front;
	}

	processflag { arg flag;
		txalasilence.processflag = flag;
		txalaonset.processflag = flag;
	}

	// modes: imitation, random (with GUI parameters), markov1, markov2
	answer {
		var halfcompass, timetogo=0;

		"SCHEDULE ANSWER".postln;
		halfcompass = (60/~bpm/2);

		// we have to make some fine tuning here removing a short time like halfcompass/10
		timetogo = txalasilence.lasthittime + halfcompass - Main.elapsedTime - (halfcompass/~answertimecorrection); // when in the future
		switch (~answermode,
			0, { this.imitation(timetogo) },
			1, { this.markov(timetogo) },
			2, { this.markov(timetogo, lastPattern.size) }
		);
	}

	imitation { arg timetogo;
		// here we would need to make sure that when it is a gap it schedules a single hit (for instance)
		lastPattern.do({arg hit, index;
			{
				this.playhit(hit.amp, 0, plank, index, lastPattern.size)
			}.defer(timetogo + hit.time);
		});
	}

	markov {arg timetogo, size=nil;
		var gap, curhits;

		if (size.isNil, {
			curhits = markov.next();
		},{
			curhits = markov.next2nd(size);
		});

		if (curhits > 0, {
			gap = ((60/~bpm/2) * ~spread) / curhits;
		},{
			gap = 0;
		});

		curhits.do({ arg index;
			var playtime = timetogo + (gap * index) + rrand(~intermakilaswing.neg, ~intermakilaswing);
			if ( playtime.isNaN, { playtime = 0 } );
			if ( playtime == inf, { playtime = 0 } );
			{
				this.playhit((~volume+rrand(-0.05, 0.05)), 0, plank, index, curhits)
			}.defer(playtime);
		});
	}

	playhit { arg amp, player, plank, index, total;
		if (index==0, { this.processflag(true) }); // repeated
		if ((index==(total-1)), { { this.processflag(false) }.defer(0.25) }); // off when the last hit stops
		Synth(\playBuf, [\amp, amp, \freq, (1+rrand(-0.003, 0.003)), \bufnum, plank.bufnum]);
		txalascoreGUI.hit(Main.elapsedTime, amp, 0, plank.bufnum);
		// if MIDI flag then send MIDIOUT here
		// if OSC flag then send OSC out messages here
		("+++++++++++++++++++++++++++"+index).postln

	}
}
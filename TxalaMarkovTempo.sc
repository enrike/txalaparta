// txalaparta-like markov chain system
// license GPL
// by www.ixi-audio.net

/*
(
s.boot;
s.waitForBoot{
	p = thisProcess.nowExecutingPath.dirname;
	t = TxalaMarkovTempo.new(s, p)
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

	var loopF, intermakilagap, server;
	var doGUI, label, reset, answer, hutsune, win;
	var txalasilence, txalaonset, markov, lastPattern;
	var presetslisten, presetmatrix, basepath;

	*new {| aserver, apath="" |
		^super.new.initTxalaMarkovTempo(aserver, apath);
	}

	initTxalaMarkovTempo { arg aserver, apath;
		server = aserver;
		basepath = apath;
		this.init();
		this.doGUI();
	}

	init {
		~bpm = 60;
		//~volume = 0.6;
		~answer = false;
		~answermode = 0; //0,1,2,3: imitation, random (with GUI parameters), markov1, markov2
		~answertimecorrection = 6;

		//~gapswing = 0.01;
		//~spread = 0.7;

		// this is to keep all the values of the listening synths in one place
		~listenparemeters = ().add(\in->0).add(\amp->1);
		~listenparemeters.tempo = ().add(\threshold->0.5).add(\falltime->0.1).add(\checkrate->20);
		~listenparemeters.onset = ().add(\threshold->0.6).add(\relaxtime->2.1).add(\floor->0.1).add(\mingap->0.1);

		lastPattern = nil;

		// TO DO: transfer to higher level. add more sounds
		//plank = Buffer.read(server, basepath ++ "/sounds/00_ugarte3.wav");

		this.start();
		this.stop();

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

	newonset { arg hittime, amp, player, plank;
		if (~txalascore.isNil.not, {
			~txalascore.hit(hittime, amp, player, plank);
		});
	}
	/////////////////////////////////////////////////////////////////////////////

	stop {
		try {
			txalasilence.kill();
			txalaonset.kill();
		} {|error|

		};
	}

	start {
		txalasilence = TxalaSilenceDetection.new(this, server, true); // parent, server, mode, answermode
		txalaonset= TxalaOnsetDetection.new(this, server);
		markov = TxalaMarkov.new;
		//~txalascore = ~txalascore.new;
	}

	reset  {
		"+++++++++ RESET ++++++++++++++++++++++++++++++++++++++++++++++++++++++++".postln;
		this.stop();
		this.start();
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

		//lastPattern.do({arg hit; hit.plank.postln});

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
				this.playhit(hit.amp, 0, index, lastPattern.size)
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
			gap = ((60/~bpm/2) * ~gap) / curhits;
		},{
			gap = 0;
		});

		curhits.do({ arg index;
			var playtime = timetogo + (gap * index) + rrand(~gapswing.neg, ~gapswing);
			if ( playtime.isNaN, { playtime = 0 } );
			if ( playtime == inf, { playtime = 0 } );
			{
				this.playhit((~amp+rrand(-0.05, 0.05)), 0, index, curhits)
			}.defer(playtime);
		});
	}

	playhit { arg amp, player, index, total;
		var plank, pos;
		if (index==0, { this.processflag(true) }); // repeated
		if ((index==(total-1)), { { this.processflag(false) }.defer(0.25) }); // off when the last hit stops

		pos = Array.fill(~buffers.size, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum); // 0 to 7
		{
			~buffersenabled[1][pos] == false; // 1 because here we are always errena
		}.while({
			pos = Array.fill(~buffers.size, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum);
		});
		plank = ~buffers[pos];

		Synth(\playBuf, [\amp, amp, \freq, (1+rrand(-0.003, 0.003)), \bufnum, plank.bufnum]);
		~txalascore.hit(Main.elapsedTime, amp, 0, plank.bufnum);
		~midiout.noteOn(player, plank.bufnum, amp*127);
		// if OSC flag then send OSC out messages here
		("+++++++++++++++++++++++++++"+index).postln

	}

	closeGUI {
		win.close()
	}


	doGUI  {
		var yloc = 35, gap=20, guielements = Array.fill(10, {nil});
		win = Window("Listening module for txalaparta",  Rect(10, 50, 355, 370));
		win.onClose = {
			txalasilence.kill();
			txalaonset.kill();
		};

		label = StaticText(win, Rect(10, 3, 130, 25));
		label.string = "BPM:" + ~bpm;

		// row of buttons on top side

		Button( win, Rect(140,3,70,25))
		.states_([
			["listen", Color.white, Color.black],
			["listen", Color.black, Color.green],
		])
		.action_({ arg but;
			if (but.value.asBoolean, {
				this.start();
			},{
				this.stop();
			})
		});
		Button( win, Rect(210,3,70,25))
		.states_([
			["answer", Color.white, Color.black],
			["answer", Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		Button( win, Rect(280,3,70,25))
		.states_([
			["pre answer", Color.white, Color.black],
			["post answer", Color.black, Color.green],
		])
		.action_({ arg but;
			txalasilence.answerposition = but.value.asBoolean;
		});

		// mode menu
		StaticText(win, Rect(10, yloc-3, 100, 25)).string = "Answer mode";
		PopUpMenu(win,Rect(95,yloc, 110,20))
		.items_(["imitation", "fixed chances", "learning"])
		.action_({ arg menu;
		    ~answermode = menu.value.asInt;
			("changing to answer mode:" + menu.item).postln;
		})
		.valueAction_(~answermode);

		Button( win, Rect(210,yloc-5,70,25))
		.states_([
			["score", Color.white, Color.black],
		])
		.action_({ arg but;
			~txalascore.doTxalaScore()
		});

		Button( win, Rect(280,yloc-5,70,25)) //Rect(140,30,70,25))
		.states_([
			["scope in", Color.white, Color.black],
		])
		.action_({ arg but;
			server.scope(1,8);
		});

		// answer time correction
		guielements[0] = EZSlider( win,
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
		guielements[1] = EZSlider( win,
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
/*		guielements[2] = EZSlider( win,
			Rect(0,yloc+(gap*3),350,20),
			"out amp",
			ControlSpec(0, 1, \lin, 0.01, 1, ""),
			{ arg ez;
				~amp = ez.value.asFloat;
			},
			initVal: ~volume,
			labelWidth: 60;
		);*/


		// DetectSilence controls //
		StaticText(win, Rect(5, yloc+(gap*4), 180, 25)).string = "Tempo detection";

		guielements[3] = EZSlider( win,
			Rect(0,yloc+(gap*5),350,20),
			"threshold",
			ControlSpec(0.01, 2, \lin, 0.01, 0.2, ""),
			{ arg ez;
				txalasilence.updatethreshold(ez.value.asFloat); // this is different because a bug? in supercollider
				~listenparemeters.tempo.threshold = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.threshold,
			labelWidth: 60;
		);//.valueAction_(~listenparemeters.tempo.threshold);

		guielements[4] = EZSlider( win,
			Rect(0,yloc+(gap*6),350,20),
			"falltime",
			ControlSpec(0.01, 20, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				txalasilence.synth.set(\falltime, ez.value.asFloat);
				~listenparemeters.tempo.falltime = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.falltime,
			labelWidth: 60;
		);

		guielements[5] = EZSlider( win,
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

		guielements[6] = EZSlider( win,
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
		guielements[7] = EZSlider( win,
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
		guielements[8] = EZSlider( win,
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
		guielements[9] = EZSlider( win,
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

		this.doPresets(win, 7, yloc+(gap*13), guielements);
		this.doMatrixGUI(win, 180, yloc+(gap*13));

		win.front;
	}


	doPresets { arg win, xloc, yloc, guielements;
		var newpreset;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Presets";

		PopUpMenu(win,Rect(xloc,yloc+20,170,20))
		.items_(presetslisten.asArray.collect({arg item; PathName.new(item).fileName}))
		.mouseDownAction_({arg menu;
			presetslisten = (basepath++"/presets_listen/*").pathMatch;
			presetslisten.insert(0, "---");
			menu.items = presetslisten.asArray.collect({arg item;
				PathName.new(item).fileName});
		})
		.action_({ arg menu;
			var data;
			("loading..." + basepath ++ "/presets_listen/" ++ menu.item).postln;
			data = Object.readArchive(basepath ++ "/presets_listen/" ++ menu.item);
			data.asCompileString.postln;

			~answertimecorrection = data[\answertimecorrection];
			//~volume = data[\volume];
			~listenparemeters = data[\listenparemeters];

			guielements[0].value = ~answertimecorrection;
			guielements[1].value = ~listenparemeters.amp;
			//guielements[2].value = ~volume;
			guielements[3].value = ~listenparemeters.tempo.threshold;
			guielements[4].value = ~listenparemeters.tempo.falltime;
			guielements[5].value = ~listenparemeters.tempo.checkrate;
			guielements[6].value = ~listenparemeters.onset.threshold;
			guielements[7].value = ~listenparemeters.onset.relaxtime;
			guielements[8].value = ~listenparemeters.onset.floor;
			guielements[9].value = ~listenparemeters.onset.mingap;
		});

		newpreset = TextField(win, Rect(xloc, yloc+42, 95, 25));
		Button(win, Rect(xloc+100,yloc+42,70,25))
		.states_([
			["save", Color.white, Color.grey]
		])
		.action_({ arg butt;
			var filename, data;
			if (newpreset.string == "",
				{filename = Date.getDate.stamp++".preset"},
				{filename = newpreset.string++".preset"}
			);

			data = Dictionary.new;

			data.put(\answertimecorrection, ~answertimecorrection);
			//data.put(\volume, ~volume);
			data.put(\listenparemeters, ~listenparemeters);

			data.writeArchive(basepath ++ "/presets_listen/" ++ filename);

			newpreset.string = ""; //clean field
		});

	}

	doMatrixGUI { arg win, xloc, yloc, guielements;
		var newpreset;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Chance matrix manager";

		PopUpMenu(win,Rect(xloc,yloc+20,170,20))
		.items_(presetmatrix.asArray.collect({arg item; PathName.new(item).fileName}))
		.mouseDownAction_({arg menu;
			presetmatrix = (basepath ++ "/presets_matrix/" ++ "*").pathMatch;
			presetmatrix.insert(0, "---");
			menu.items = presetmatrix.asArray.collect({arg item;
				PathName.new(item).fileName});
		})
		.action_({ arg menu;
			var data;
			("loading..." + basepath  ++ "/presets_matrix/" ++  menu.item).postln;
			data = Object.readArchive(basepath  ++ "/presets_matrix/" ++  menu.item);
			data.asCompileString.postln;

			markov.new2ndmatrix( data[\beatdata] );

			markov.beatdata2nd.plot;

		});

		newpreset = TextField(win, Rect(xloc, yloc+42, 95, 25));
		Button(win, Rect(xloc+100,yloc+42,70,25))
		.states_([
			["save", Color.white, Color.grey]
		])
		.action_({ arg butt;
			var filename, data;
			if (newpreset.string == "",
				{filename = Date.getDate.stamp++".preset"},
				{filename = newpreset.string++".preset"}
			);

			data = Dictionary.new;

			data.put(\beatdata, markov.beatdata2nd);

			markov.beatdata2nd.plot;

			data.writeArchive(basepath ++ "/presets_matrix/" ++ filename);

			newpreset.string = ""; //clean field
		});
	}
}
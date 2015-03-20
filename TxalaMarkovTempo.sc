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
	var txalasilence, txalaonset, markov, ann, lastPattern;
	var presetslisten, presetmatrix, basepath, sndpath, <samples;
	var planksMenus;

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
		~amp = 1;
		~answer = false;
		~answermode = 0; //0,1,3: imitation, markov1, markov2
		~answertimecorrection = 0.08; // compensate latency
		~hutsunelookup = 0.5;

		~gap = 0.65;
		~gapswing = 0.01;

		if (~buffer.isNil, {
			~buffers = Array.fill(8, {nil});
		});

		if (~plankchance.isNil, {
			~plankchance = (Array.fill(~buffers.size, {1}));
		});

		if (~buffersenabled.isNil, {
			~buffersenabled = [Array.fill(~buffers.size, {false}), Array.fill(~buffers.size, {false})];
		});

		//planksMenus = Array.fill(~buffers.size, {[nil,nil,nil]});

		// this is to keep all the values of the listening synths in one place
		~listenparemeters = ().add(\in->0).add(\amp->1);
		~listenparemeters.tempo = ().add(\threshold->0.5).add(\falltime->0.1).add(\checkrate->20);
		~listenparemeters.onset = ().add(\threshold->0.6).add(\relaxtime->2.1).add(\floor->0.1).add(\mingap->0.1);

		lastPattern = nil;

		sndpath = basepath ++ "/sounds/";
		samples = (sndpath++"*").pathMatch;
		("sndpath is" + sndpath).postln;
		("available samples are" + samples).postln;

		~buffers = Array.fill(8, {nil});

/*		MIDIClient.init;
		MIDIClient.destinations;
		MIDIIn.connectAll;
		~midiout = MIDIOut(0, MIDIClient.destinations.at(0).uid);*/

		~txalascore = TxalaScoreGUI.new;

		SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
			Out.ar(outbus,
				amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
			)
		}).add;

		this.start();
		this.stop();

	}


	// SYNTH'S CALLBACKS /////////////////////////////////////////////////////////////////
	hutsune {
		lastPattern = nil;
	}

	loop {
		{ label.string = "BPM" + ~bpm + "     Compass" + txalasilence.compass}.defer
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
		if (txalasilence.isNil.not, {
			txalasilence.kill();
			txalasilence=nil;
		});
		if (txalaonset.isNil.not, {
			txalaonset.kill();
			txalaonset=nil;
		});
		txalasilence = TxalaSilenceDetection.new(this, server); // parent, server, mode, answermode
		txalaonset = TxalaOnsetDetection.new(this, server);
		markov = TxalaMarkov.new;
		ann = TxalaAnn.new;
		~txalascore.reset();
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
	// called from child that detects bpm and group ends
	answer {
		var halfcompass, defertime=0;

		halfcompass = (60/~bpm/2);

		// we have to make some fine tuning here removing a short time like halfcompass/10
		defertime = txalasilence.lasthittime + halfcompass - SystemClock.seconds - ~answertimecorrection; // when in the future
		["will hit back in", defertime, lastPattern.size].postln;

		if (defertime.isNaN.not, {
			switch (~answermode,
				0, { this.imitation(defertime) },
				1, { this.markovnext(defertime) },
				2, { this.markovnext(defertime, lastPattern.size) },
				3, { this.annnext(defertime, lastPattern.size) }
			);
		});
	}

	imitation { arg defertime;
		// here we would need to make sure that when it is a gap it schedules a single hit (for instance)
		lastPattern.do({arg hit, index;
			{
				this.playhit(hit.amp, 0, index, lastPattern.size)
			}.defer(defertime + hit.time);
		});
	}

	markovnext {arg defertime, size=nil;
		var gap=0, curhits;

		if (size.isNil, {
			curhits = markov.next();
		},{
			curhits = markov.next2nd(size);
				[curhits, size].postln;
		});

		if (curhits > 0, { gap = ((60/~bpm/2) * ~gap) / curhits });

		curhits.do({ arg index;
			var playtime = defertime + (gap * index) + rrand(~gapswing.neg, ~gapswing);
			if ( playtime.isNaN, { playtime = 0 } );
			if ( playtime == inf, { playtime = 0 } );
			{
				this.playhit((~amp+rrand(-0.05, 0.05)), 0, index, curhits)
			}.defer(playtime);
		});
	}

	annnext{arg defertime, size=nil;
		var gap=0, curhits;

		curhits = ann.next(size);

		if (curhits > 0, { gap = ((60/~bpm/2) * ~gap) / curhits });

		curhits.do({ arg index;
			var playtime = defertime + (gap * index) + rrand(~gapswing.neg, ~gapswing);
			if ( playtime.isNaN, { playtime = 0 } );
			if ( playtime == inf, { playtime = 0 } );
			{
				this.playhit((~amp+rrand(-0.05, 0.05)), 0, index, curhits)
			}.defer(playtime);
		});

	}

	playhit { arg amp, player, index, total;
		var plank, pos;

		if (index==0, { this.processflag(true) }); // stop listening to incomming hits. dont listen to yourself

		// in the future we should use a complex system that takes into consideration the users input
		pos = Array.fill(~buffers.size, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum); // 0 to 7
		{
			~buffersenabled[1][pos] == false; // 1 because here we are always errena
		}.while({
			pos = Array.fill(~buffers.size, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum);
		});

		plank = ~buffers[pos];

		if ((index==(total-1)), { // listen again when the last hit stops
			var hitlength = plank.numFrames/plank.sampleRate;
			hitlength = hitlength - ((hitlength/3)*2); // remove the tail 2/3
			{ this.processflag(false) }.defer(hitlength)
		});

		Synth(\playBuf, [\amp, amp, \freq, (1+rrand(-0.003, 0.003)), \bufnum, plank.bufnum]);
		if (~txalascore.isNil.not, { ~txalascore.hit(SystemClock.seconds, amp, 0, plank.bufnum) });
		//~midiout.noteOn(player, plank.bufnum, amp*127);
		//{~midiout.noteOff(player, plank.bufnum, amp*127) }.defer(0.2);
		// if OSC flag then send OSC out messages here
		("+++++++++++++++++++++++++++"+index).postln
	}

	closeGUI {
		win.close();
	}


	doGUI  {
		var yindex=0, yloc = 35, gap=20, guielements = (); //Array.fill(10, {nil});
		win = Window("Listening module for txalaparta",  Rect(10, 50, 700, 570));
		win.onClose = {
			txalasilence.kill();
			txalaonset.kill();
			if (~txalascore.isNil.not, {~txalascore.close});
		};

		label = StaticText(win, Rect(10, 0, 250, 25));
		label.string = "BPM: ---";

		// row of buttons on top side

		Button( win, Rect(70,yloc-10,70,25))
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
		Button( win, Rect(140,yloc-10,70,25))
		.states_([
			["answer", Color.white, Color.black],
			["answer", Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		Button( win, Rect(210,yloc-10,70,25))
		.states_([
			["score", Color.white, Color.black],
		])
		.action_({ arg but;
			var num;
			num = this.getnumactiveplanks();
			~txalascore.doTxalaScore(numactiveplanks:num);
		});

		Button( win, Rect(280,yloc-10,70,25)) //Rect(140,30,70,25))
		.states_([
			["scope in", Color.white, Color.black],
		])
		.action_({ arg but;
			server.scope(1,8);
		});

		yindex = yindex + 1;

		// amplitudes
		// incomming amp correction
		guielements.add(\inamp-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"in amp",
			ControlSpec(0, 2, \lin, 0.01, 1, ""),
			{ arg ez;
				txalaonset.synth.set(\amp, ez.value.asFloat);
				txalasilence.synth.set(\amp, ez.value.asFloat);
				~listenparemeters.amp = ez.value.asFloat;
			},
			initVal: ~listenparemeters.amp,
			labelWidth: 60;
		));

		yindex = yindex + 1;

		// ~amplitude
		guielements.add(\amp-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"out amp",
			ControlSpec(0, 1, \lin, 0.01, 1, ""),
			{ arg ez;
				~amp = ez.value.asFloat;
			},
			initVal: ~amp,
			labelWidth: 60;
		));


		yindex = yindex + 1.5;

		// mode menu
		StaticText(win, Rect(10, yloc+(gap*yindex), 120, 25)).string = "Answer mode";
		guielements.add(\answermode->
				PopUpMenu(win,Rect(95,yloc+(gap*yindex), 150,20))
				.items_(["imitation", "fixed chances", "learning chances", "learning ANN"])
				.action_({ arg menu;
					~answermode = menu.value.asInt;
					("changing to answer mode:" + menu.item).postln;
				})
				.valueAction_(~answermode)
			);

		yindex = yindex + 1;

		// answer time correction
		guielements.add(\answertimecorrection->  EZSlider( win,
		 	Rect(0,yloc+(gap*yindex),350,20),
		 	"latency",
		 	ControlSpec(-1, 1, \lin, 0.01, 0, ""),
		 	{ arg ez;
		 		~answertimecorrection = ez.value.asFloat;
		 	},
		 	initVal: ~answertimecorrection,
		 	labelWidth: 60;
		 ));

		yindex = yindex + 1;

		guielements.add(\gap->  EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"spread",
			ControlSpec(0, 1, \lin, 0.01, 1, ""),
			{ arg ez;
				~gap = ez.value.asFloat;
			},
			initVal: ~gap,
			labelWidth: 60;
			));

		yindex = yindex + 1;

		guielements.add(\gapswing-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"swing",
			ControlSpec(0, 0.2, \lin, 0.01, 0.2, ""),
			{ arg ez;
				~gapswing = ez.value.asFloat;
			},
			initVal: ~gapswing,
			labelWidth: 60;
			));


yindex = yindex + 1.5;

		// DetectSilence controls //
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Tempo detection";

		yindex = yindex + 1;

		guielements.add(\tempothreshold->
		EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"threshold",
			ControlSpec(0.01, 2, \lin, 0.01, 0.2, ""),
			{ arg ez;
				txalasilence.updatethreshold(ez.value.asFloat); // this is different because a bug? in supercollider
				~listenparemeters.tempo.threshold = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.threshold,
			labelWidth: 60;
			));//.valueAction_(~listenparemeters.tempo.threshold);

		yindex = yindex + 1;

		guielements.add(\falltime->
		EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"falltime",
			ControlSpec(0.01, 20, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				txalasilence.synth.set(\falltime, ez.value.asFloat);
				~listenparemeters.tempo.falltime = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.falltime,
			labelWidth: 60;
			));

		yindex = yindex + 1;

		guielements.add(\checkrate-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"rate",
			ControlSpec(5, 60, \lin, 1, 30, ""),
			{ arg ez;
				txalasilence.synth.set(\checkrate, ez.value.asFloat);
				~listenparemeters.tempo.checkrate = ez.value.asFloat;
			},
			initVal: ~listenparemeters.tempo.checkrate,
			labelWidth: 60;
			));

yindex = yindex + 1.5;

		// hutsune timeout control
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Hutsune detection";

		yindex = yindex + 1;

		guielements.add(\hutsunelookup ->  EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"lookup",
			ControlSpec(0, 1, \lin, 0.01, 1, ""),
			{ arg ez;
				~hutsunelookup = ez.value.asFloat;
			},
			initVal: ~hutsunelookup,
			labelWidth: 60;
			));

		yindex = yindex + 1.5;

		// Onset pattern detection controls //
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Hit onset detection";

		yindex = yindex + 1;

		guielements.add(\onsetthreshold->  EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"threshold",
			ControlSpec(0, 1, \lin, 0.01, 0.4, ""),
			{ arg ez;
				txalaonset.synth.set(\threshold, ez.value.asFloat);
				~listenparemeters.onset.threshold = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.threshold,
			labelWidth: 60;
			));

		yindex = yindex + 1;

		guielements.add(\relaxtime->  EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"relaxtime",
			ControlSpec(0.01, 4, \lin, 0.01, 2.1, "ms"),
			{ arg ez;
				txalaonset.synth.set(\relaxtime, ez.value.asFloat);
				~listenparemeters.onset.relaxtime = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.relaxtime,
			labelWidth: 60;
			));

		yindex = yindex + 1;

		guielements.add(\floor->  EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"floor",
			ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
			{ arg ez;
				txalaonset.synth.set(\floor, ez.value.asFloat);
				~listenparemeters.onset.floor = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.floor,
			labelWidth: 60;
			));

		yindex = yindex + 1;

		guielements.add(\mingap->  EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"mingap",
			ControlSpec(0.1, 20, \lin, 0.1, 0.1, "Ms"),
			{ arg ez;
				txalaonset.synth.set(\mingap, ez.value.asFloat);
				~listenparemeters.onset.mingap = ez.value.asFloat;
			},
			initVal: ~listenparemeters.onset.mingap,
			labelWidth: 60;
			));

		yindex = yindex + 1.5;

		this.doPresets(win, 7, yloc+(gap*yindex), guielements);
		this.doMatrixGUI(win, 180, yloc+(gap*yindex));

		this.doPlanks(350,yloc, 20, 220, 20);

		win.front;
	}


	doPlanks { arg xloc, yloc, gap, width, height;
		var menuxloc = xloc + 44;
		var playxloc = menuxloc+200+2;

		// PLANKS - OHOLAK //////////////////////////////////
		StaticText(win, Rect(xloc+22, yloc-18, 200, 20)).string = "ER";
		StaticText(win, Rect(menuxloc, yloc-18, 200, 20)).string = "Oholak/Planks";
		StaticText(win, Rect(menuxloc+230, yloc-16, 200, 20)).string = "% chance";

		planksMenus = Array.fill(~buffers.size, {[nil,nil]});

		////////////////
		~buffers.size.do({ arg index;

			// errena row buttons
			planksMenus[index][0] = Button(win, Rect(xloc+22,yloc+(gap*index),20,20))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.black, Color.blue],
			])
			.action_({ arg butt;
				~buffersenabled[1][index] = butt.value.asBoolean; // [[false...],[false...]]
				this.updateTxalaScoreNumPlanks();
			});

			if (index==0, {
				planksMenus[index][0].valueAction = 1;
				//planksMenus[index][1].valueAction = 1;
			});// ONLY activate first ones

			// menus for each plank
			planksMenus[index][1] = PopUpMenu(win,Rect(menuxloc,yloc+(gap*index),200,20))
			.items_( samples.asArray.collect({arg item; PathName.new(item).fileName}) )
			.action_({ arg menu;
				var item = nil;
				try {
					 item = menu.item.postln;
				} {|error|
					"empty slot".postln;
				};

				if (item.isNil.not, {
					~buffers[index] = Buffer.read(server, sndpath ++ menu.item);
				});
/*				try {
					~buffers[index] = Buffer.read(server, sndpath ++ menu.item);
				} {|error|
					"empty slot".postln;
				};*/
			})
			.valueAction_(index);

			// play buttons row
			Button(win, Rect(playxloc,yloc+(gap*index),20,20))
			.states_([
				[">", Color.white, Color.black]
			])
			.action_({ arg butt;// play a single shot
				if (~buffers[index].isNil.not, {
					Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, ~buffers[index].bufnum])
				})
			});

			Slider(win,Rect(menuxloc+225,yloc+(gap*index),75,20))
			.action_({arg sl;
				~plankchance[index] = sl.value;
			})
			.orientation_(\horizontal)
			.valueAction_(1);
		});

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
			~amp = data[\amp];
			~gap = data[\gap];
			~gapswing = data[\gapswing];
			~answermode = data[\answermode];
			~hutsunelookup = data[\hutsunelookup];
			~listenparemeters = data[\listenparemeters];

			[guielements].postln;

			// is the saved data correct?
			guielements.gap.valueAction = ~gap;
			guielements.gapswing.valueAction = ~gapswing;
			guielements.answermode.valueAction = ~answermode; //menu
			guielements.hutsunelookup.valueAction = ~hutsunelookup;
			guielements.answertimecorrection.valueAction = ~answertimecorrection;
			guielements.amp.valueAction = ~amp;

			guielements.inamp.valueAction = ~listenparemeters.amp;
			guielements.tempothreshold.valueAction = ~listenparemeters.tempo.threshold;
			guielements.falltime.valueAction = ~listenparemeters.tempo.falltime;
			guielements.checkrate.valueAction = ~listenparemeters.tempo.checkrate;
			guielements.onsetthreshold.valueAction = ~listenparemeters.onset.threshold;
			guielements.relaxtime.valueAction = ~listenparemeters.onset.relaxtime;
			guielements.floor.valueAction = ~listenparemeters.onset.floor;
			guielements.mingap.valueAction = ~listenparemeters.onset.mingap;
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
			data.put(\amp, ~amp);
			data.put(\listenparemeters, ~listenparemeters);
			data.put(\hutsunelookup, ~hutsunelookup);
			data.put(\gap, ~gap);
			data.put(\gapswing, ~gapswing);
			data.put(\answermode, ~answermode);

			(basepath ++ "/presets_listen/" ++ filename).postln;

			data.writeArchive(basepath ++ "/presets_listen/" ++ filename);

			newpreset.string = ""; //clean field
		});

	}

	doMatrixGUI { arg win, xloc, yloc, guielements;
		var newpreset;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Chance matrix manager";

		yloc = yloc+20;

		Button(win, Rect(xloc,yloc,170,25))
		.states_([
			["update matrix", Color.white, Color.grey],
			["update matrix", Color.white, Color.green]
		])
		.action_({ arg butt;
			markov.update = butt.value.asBoolean;
		});

		yloc = yloc+27;
		PopUpMenu(win,Rect(xloc,yloc,170,20))
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

			markov.loaddata( data[\beatdata] );
			//markov.beatdata2nd.plot;

		});

		yloc = yloc+22;
		newpreset = TextField(win, Rect(xloc, yloc, 95, 25));

		Button(win, Rect(xloc+100,yloc,70,25))
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
			data.writeArchive(basepath ++ "/presets_matrix/" ++ filename);

			newpreset.string = ""; //clean field
		});
	}


	updateTxalaScoreNumPlanks {
		var numactiveplanks = this.getnumactiveplanks();
		if (~txalascore.isNil.not, {~txalascore.updateNumPlanks( numactiveplanks ) });
	}

	getnumactiveplanks {
		var numactiveplanks=0;
		~buffers.do({arg arr, ind; // checks if enabled for any of the players
			if( (~buffersenabled[0][ind]||~buffersenabled[1][ind]),
				{numactiveplanks = numactiveplanks + 1})});
		^numactiveplanks;
	}

}
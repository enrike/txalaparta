// txalaparta-like markov chain system
// license GPL
// by www.ixi-audio.net

/*
(
s.boot;
s.waitForBoot{
	p = thisProcess.nowExecutingPath.dirname;
	t = TxalaInteractive.new(s, p)
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

+ number of hits per group or phrase: OnSets
- distance between hits
- amplitude of each hit
- pitch of each hit (to do...)
*/

TxalaInteractive{

	var loopF, intermakilagap, server, tempocalc;
	var doGUI, label, reset, answer, hutsune, win, scope, scopesynth;
	var txalasilence, txalaonset, markov, ann, lastPattern, patternbank;
	var presetslisten, presetmatrix, basepath, sndpath, <samples;
	var planksMenus, hitbutton, compassbutton, prioritybutton, hutsunebutton, numbeatslabel, selfcancelation=false;
	var <pitchbuttons, <>plankdata, circleanim, drawingSet;

	var numplanks = 6; // max planks
	var plankresolution = 5; // max positions per plank
	var ampresolution = 5; // max amps per position

	*new {| aserver, apath="" |
		^super.new.initTxalaInteractive(aserver, apath);
	}

	initTxalaInteractive { arg aserver, apath;
		server = aserver;
		basepath = apath;
		plankdata = [[],[],[],[],[],[]];
		this.init();
		this.doGUI();
	}

	init {
		~bpm = 60;
		~amp = 1;
		~answer = false;
		~answerpriority = false; // true if answer on group end (sooner), false if answer from group start (later)
		~autoanswerpriority = true;
		~answermode = 0; //0,1,3: imitation, markov1, markov2
		~hutsunelookup = 0.3;
		~plankdetect = 1;

		~gapswing = 0.01;

		~buffers = Array.fillND([numplanks, plankresolution, ampresolution], { nil });

		drawingSet = Array.fill(~buffers.size, {[-1, 0, false, 10]}); // why ~buffers.size??

		// this is to keep all the values of the listening synths in one place
		~listenparemeters = ().add(\in->0).add(\gain->1);
		~listenparemeters.tempo = ().add(\threshold->0.5).add(\falltime->0.1).add(\checkrate->20);
		~listenparemeters.onset = ().add(\threshold->0.4).add(\relaxtime->0.01).add(\floor->0.1).add(\mingap->1);

		lastPattern = nil;

		sndpath = basepath ++ "/sounds/";
		samples = (sndpath++"*").pathMatch;
		("sndpath is" + sndpath).postln;
		("available samples are" + samples).postln;

		pitchbuttons = Array.fill(~buffers.size, {nil});

		markov = TxalaMarkov.new;
		patternbank = TxalaPatternBank.new;
		tempocalc = TempoCalculator.new(2);
		~txalascore = TxalaScoreGUI.new;
		//ann = TxalaAnn.new;

/*		MIDIClient.init;
		MIDIClient.destinations;
		MIDIIn.connectAll;
		~midiout = MIDIOut(0, MIDIClient.destinations.at(0).uid);*/

		SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
			Out.ar(outbus,
				amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
			)
		}).add;
	}


	loadsampleset{ arg presetfilename;
		var foldername = presetfilename.split($.)[0];// get rid of the extension
		("load sampleset"+foldername).postln;
		~buffers.do({arg plank, indexplank;
			plank.do({ arg pos, indexpos;
				pos.do({ arg amp, indexamp;
					var filename="plank";
					filename = filename ++ indexplank.asString++indexpos.asString++indexamp.asString++".wav";
					if ( PathName.new(sndpath ++"/"++foldername++"/"++filename).isFile, {
						~buffers[indexplank][indexpos][indexamp] = Buffer.read(server, sndpath ++"/"++foldername++"/"++filename);
					})
				})
			})
		})
	}


	// SYNTH'S CALLBACKS /////////////////////////////////////////////////////////////////
	hutsune {
		lastPattern = ();
		txalasilence.compass =  txalasilence.compass + 1;
		if(~answer, { this.answer(0) }); //asap
		if (~txalascore.isNil.not, {
			var last = SystemClock.seconds-((60/~bpm)/2);
			~txalascore.hit(last, -1, 1, 0) ; // -1 for hutsune
			~txalascore.mark(last, (last+((60/~bpm)/4)), txalasilence.compass, lastPattern.size)
		});
		tempocalc.pushlasttime(); // empty hits also count for BPM calc
		{hutsunebutton.value = 1}.defer;
		{hutsunebutton.value = 0}.defer(0.2);
	}

	loop {
		{ label.string = "BPM:" + ~bpm + "\nCompass:" + txalasilence.compass}.defer
	}


	broadcastgroupstarted { // silence detection calls this.
		~bpm = tempocalc.calculate();
		if( (~answer && ~answerpriority.not), { this.answer() }); // later
		{compassbutton.value = 1}.defer;
	}

	broadcastgroupended { // silence detection calls this.
		lastPattern = txalaonset.closegroup(); // to close beat group in the onset detector
		patternbank.addpattern(lastPattern); // store into bank in case it wasnt there
		if( (~answer && ~answerpriority), {this.answer()}); // asap
		if (~autoanswerpriority, { this.doautoanswerpriority() });
		if (~txalascore.isNil.not, {
			~txalascore.mark(tempocalc.lasttime, SystemClock.seconds, txalasilence.compass, lastPattern.size)
		});
		{numbeatslabel.string = "Beats:" + lastPattern.size;}.defer;
		{compassbutton.value = 0}.defer; // display now
	}

	newonset { arg hittime, amp, player, plank;
		if (~txalascore.isNil.not, { ~txalascore.hit(hittime, amp, player, plank) });
		{hitbutton.value = 1}.defer;
		{hitbutton.value = 0}.defer(0.055);
	}

	/////////////////////////////////////////////////////////////////////////////

	// activates/deactivates answerpriority if to tight to answer with priority
	doautoanswerpriority {
		var defertime;
		defertime = tempocalc.lasttime + (60/~bpm/2) - SystemClock.seconds;
		~answerpriority = defertime > 0;
		{ prioritybutton.value = ~answerpriority.asInt }.defer;
	}

	stop {
		if (txalasilence.isNil.not, {
			txalasilence.kill();
			txalasilence=nil;
		});
		if (txalaonset.isNil.not, {
			txalaonset.kill();
			txalaonset=nil;
		});
	}

	start {
		if (txalasilence.isNil.not, {
			txalasilence.kill();
			txalasilence=nil;
		});
		if (txalaonset.isNil.not, {
			plankdata = txalaonset.plankdata;// will restore itself on new()
			txalaonset.kill();
			txalaonset=nil;
		});
		txalasilence = TxalaSilenceDetection.new(this, server); // parent, server, mode, answermode
		txalaonset = TxalaOnsetDetection.new(this, server);
		~txalascore.reset();
		tempocalc.reset();
	}

	reset  {
		this.stop();
		this.start();
	}

	processflag { arg flag;
		txalasilence.processflag = flag;
		txalaonset.processflag = flag;
	}

	// called from silencedetect that detects bpm and group ends
	answer {arg defertime;
		// calc when in future should answer be. start from last detected hit and use tempo to calculate
		if (defertime.isNil, {
			defertime = tempocalc.lasttime + (60/~bpm/2) - SystemClock.seconds;
		});

		if (defertime.isNaN.not, {
			switch (~answermode,
				0, { this.imitation(defertime) },
				1, { this.markovnext(defertime) }, // fixed values chain
				2, { this.markovnext(defertime, lastPattern.size, 2) },
				3, { this.markovnext(defertime, lastPattern.size, 3) },
				4, { this.markovnext(defertime, lastPattern.size, 4) }
			);
		});
	}

	imitation { arg defertime;
		lastPattern.do({arg hit, index;
			{
				this.playhit(hit.amp, 0, index, lastPattern.size, hit.plank)
			}.defer(defertime + hit.time);
		});
	}

	// analysing of lastPattern
	averageamp { // returns average amp from hits in last phrase
		var val=0;
		if (lastPattern.size>0, {
			lastPattern.do({ arg hit;
				val = val + hit.amp;
			});
			val = val/(lastPattern.size);
		}, {
			val = 0.5;
		});
		^val;
	}

	averagegap { // returns average gap time between hits in last phrase
		var val=0;
		if (lastPattern.size > 1, {
			lastPattern.do({ arg hit, index;
				if (index > 0, { //sum all gaps
					val = val + (hit.time-lastPattern[index-1].time);
				});
			});
			val = val / (lastPattern.size-1); // num of gaps is num of hits-1
		}, {
			val = 0.15; // if it was an hutsune or ttan. should we calculate this according to current bpm?
		});
		if (val < 0.07, {val = 0.07}); //lower limit
		^val;
	}

	getaccent{ // check if first or last hit are accentuated. true 1st / false 2nd
		var res;
		if (lastPattern.size > 0, {
			res = (lastPattern.first.amp >= lastPattern.last.amp);
		},{
			res = true;
		});
		^res
	}

	markovnext {arg defertime=0, size=nil, order=2;
		var gap=0, curhits, lastaverageamp = this.averageamp(), hitpattern;

		if (size.isNil, {
			curhits = markov.next();
		},{
			switch (order,
				2, { curhits = markov.next2nd(size) },
				3, { curhits = markov.next3rd(size) },
				4, { curhits = markov.next4th(size) }
			);
		});

		if (curhits > 1, { gap = this.averagegap() });
		// should we shorten the gap according to num of curhits??

		if (curhits==1 && [true, false].wchoose([0.2, 0.8]), {// here every random time play a chord
			gap = 0;
			curhits = 2;
		});

		if ( defertime < 0, {curhits = 1}); // if we are late or gaps it too shot they pile up and sounds horrible

		hitpattern = patternbank.getrandpattern(curhits); // just get any corresponding to curhits num by now

		curhits.do({ arg index;
			var playtime, amp;
			playtime = defertime + (gap * index) + rrand(~gapswing.neg, ~gapswing);
			amp = (lastaverageamp + rrand(-0.05, 0.05)) * ~amp; // adapt amplitude to prev detected

			if (this.getaccent, {
				if ((index==0), { amp = amp + rand(0.02, 0.05) });// accent first
			}, {
				if ((index==(curhits-1)), { amp = amp + rand(0.02, 0.05) }) // accent last;
			});

			if ( playtime.isNaN, { playtime = 0 } );
			if ( playtime == inf, { playtime = 0 } );
			{ this.playhit( amp, 0, index, curhits, hitpattern.pattern[index].plank) }.defer(playtime); //
			// I need a task that calls circleanim.scheduleDraw(data)
			// data must be like drawingSet = Array.fill(~buffers.size, {[-1, 0, false, 10]});
			drawingSet[index] = [0, playtime, true, amp]; // store for drawing on window.refresh
			{circleanim.scheduleDraw(drawingSet)}.defer;
		});
	}

	selfcancel { arg plank, index, total;
		if (selfcancelation, { // dont listen while I am playing myself
			if (index==0, { this.processflag(true) });

			if ((index==(total-1)), { // listen again when the last hit stops
				var hitlength = plank.numFrames/plank.sampleRate;
				hitlength = hitlength * 0.4; // but remove the sound tail. expose this in the GUI?
				{ this.processflag(false) }.defer(hitlength)
			});
		});
	}

	playhit { arg amp=0, player=0, index=0, total=0, plank;
		var actualplank, plankpos, plankamp, ranges;
		this.selfcancel(plank, index, total); // only if enabled by user

		// need to check if all slots are full
		plankpos = Array.fill(plankresolution, {arg n=0; n}).wchoose([0.15, 0.15, 0.3, 0.3, 0.1]); // focus on plank center

		// need to check if all slots are full
		ranges = Array.fill(ampresolution, {arg num=0; (1/ampresolution)*(num+1)}); // which sample corresponds to this amp
		plankamp = ranges.detectIndex({arg item; amp<=item});

		actualplank = ~buffers[plank][plankpos][plankamp]; //
		//["the sample to play", amp, plank, plankpos, plankamp].postln;

		Synth(\playBuf, [\amp, amp, \freq, (1+rrand(-0.003, 0.003)), \bufnum, actualplank.bufnum]);
		if (~txalascore.isNil.not, { ~txalascore.hit(SystemClock.seconds, amp, 0, plank) });

		//~midiout.noteOn(player, plank.bufnum, amp*127);
		// if OSC flag then send OSC out messages here
	}

	closeGUI {
		win.close();
	}

	doGUI  {
		var yindex=0, yloc = 35, gap=20, guielements = (); //Array.fill(10, {nil});
		win = Window("Interactive txalaparta",  Rect(10, 50, 700, 550));
		win.onClose = {
			if (txalasilence.isNil.not, {txalasilence.kill()});
			if (txalaonset.isNil.not, {txalaonset.kill()});
			if (~txalascore.isNil.not, {~txalascore.close});
			scopesynth.free;
			scope.free;
		};

		// row of buttons on top side

		Button( win, Rect(0,0,140,35))
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
		Button( win, Rect(0,yloc,140,35))
		.states_([
			["answer", Color.white, Color.black],
			["answer", Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		Button( win, Rect(180,0,80,25))
		.states_([
			["auto priority", Color.white, Color.black],
			["auto priority", Color.black, Color.green]
		])
		.action_({ arg but;
			~autoanswerpriority = but.value.asBoolean;
		}).valueAction_(~autoanswerpriority);

		prioritybutton = Button( win, Rect(180,yloc-10,80,25))
		.states_([
			["priority", Color.white, Color.black],
			["priority", Color.black, Color.green]
		])
		.action_({ arg but;
			~answerpriority = but.value.asBoolean;
		}).valueAction_(~answerpriority);

		Button( win, Rect(180,yloc+15,80,25))
		.states_([
			["cancel me", Color.white, Color.black],
			["cancel me", Color.black, Color.red]
		])
		.action_({ arg but;
			selfcancelation = but.value.asBoolean;
		});

		Button(win,  Rect(260,0,80,25))
		.states_([
			["show score", Color.white, Color.black],
		])
		.action_({ arg butt;
			var num = 1;
			try{ num = this.numactiveplanks() };
			~txalascore.doTxalaScore(numactiveplanks:num);
			~txalascore.reset();
		});

		Button( win, Rect(260,yloc-10,80,25)) //Rect(140,30,70,25))
		.states_([
			["scope in", Color.white, Color.black],
		])
		.action_({ arg but;
			if (scopesynth.isNil, {
				SynthDef(\test, { |in=0, gain=1, out=25|
					Out.ar(out, SoundIn.ar(in)*gain);
				}).add;
				{ scopesynth = Synth(\test) }.defer(0.5);
			});

			server.scope(1,25);//bus 25 from the txalaonset synth
		});

		yindex = yindex + 2.3;

		// ~gain
		guielements.add(\gain-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"gain in",
			ControlSpec(0, 5, \lin, 0.01, 1, ""),
			{ arg ez;
				~listenparemeters.gain = ez.value.asFloat;
				if (scopesynth.isNil.not, {scopesynth.set(\gain, ez.value.asFloat)});
				if (txalasilence.isNil.not, {
					txalasilence.synth.set(\gain, ez.value.asFloat);
				});
				if (txalaonset.isNil.not, {
					txalaonset.synth.set(\gain, ez.value.asFloat);
				});
			},
			initVal: ~listenparemeters.gain,
			labelWidth: 60;
		));

		yindex = yindex + 1;

		// ~amplitude
		guielements.add(\amp-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"volume",
			ControlSpec(0, 1, \lin, 0.01, 1, ""),
			{ arg ez;
				~amp = ez.value.asFloat;
			},
			initVal: ~amp,
			labelWidth: 60;
		));

		yindex = yindex + 1.5;

		// mode menu
		StaticText(win, Rect(7, yloc+(gap*yindex)-3, 200, 25)).string = "Answer mode";
		guielements.add(\answermode->
				PopUpMenu(win,Rect(95,yloc+(gap*yindex), 210,20))
			.items_(["imitation",
				"fixed chances",
				"learning chances 2 compasses",
				"learning chances 3 compasses",
				"learning chances 4 compasses"])
				.action_({ arg menu;
					~answermode = menu.value.asInt;
					("changing to answer mode:" + menu.item).postln;
				})
				.valueAction_(~answermode)
			);

		yindex = yindex + 1.5;

		guielements.add(\gapswing-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"gapswing",
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
				"threshold",// we use mouseUpAction because bug in DetectSilence class. cannot RT update this parameter
				ControlSpec(0.01, 2, \lin, 0.01, 0.2, ""),
				nil,
				initVal: ~listenparemeters.tempo.threshold,
				labelWidth: 60;
			).sliderView.mouseUpAction_({arg ez;
				if (txalasilence.isNil.not, {
					txalasilence.updatethreshold(ez.value.asFloat);
				});
				~listenparemeters.tempo.threshold = ez.value.asFloat;
			});
		);

		yindex = yindex + 1;

		guielements.add(\falltime->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"falltime",
				ControlSpec(0.01, 3, \lin, 0.01, 0.1, "Ms"),
				{ arg ez;
					if (txalasilence.isNil.not, {
						txalasilence.synth.set(\falltime, ez.value.asFloat);
					});
					~listenparemeters.tempo.falltime = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.falltime,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\checkrate->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"rate",
				ControlSpec(5, 60, \lin, 1, 30, ""),
				{ arg ez;
					if (txalasilence.isNil.not, {
						txalasilence.synth.set(\checkrate, ez.value.asFloat);
					});
					~listenparemeters.tempo.checkrate = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.checkrate,
				labelWidth: 60;
		));

		yindex = yindex + 1.5;

		// hutsune timeout control
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Hutsune detection timeout";

		yindex = yindex + 1;

		guielements.add(\hutsunelookup ->
			EZSlider( win,
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

		guielements.add(\onsetthreshold->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"threshold",
				ControlSpec(0, 1, \lin, 0.01, 0.4, ""),
				{ arg ez;
					if (txalaonset.isNil.not, {
						txalaonset.synth.set(\threshold, ez.value.asFloat);
					});
					~listenparemeters.onset.threshold = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.threshold,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\relaxtime->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"relaxtime",
				ControlSpec(0.001, 0.5, \lin, 0.001, 0.05, "ms"),
				{ arg ez;
					if (txalaonset.isNil.not, {
						txalaonset.synth.set(\relaxtime, ez.value.asFloat);
					});
					~listenparemeters.onset.relaxtime = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.relaxtime,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\floor->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"floor",
				ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
				{ arg ez;
					if (txalaonset.isNil.not, {
						txalaonset.synth.set(\floor, ez.value.asFloat);
					});
					~listenparemeters.onset.floor = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.floor,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\mingap->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"mingap",
				ControlSpec(1, 128, \lin, 1, 1, "FFT frames"),
				{ arg ez;
					if (txalaonset.isNil.not, {
						txalaonset.synth.set(\mingap, ez.value.asFloat);
					});
					~listenparemeters.onset.mingap = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.mingap,
				labelWidth: 60;
		));

		yindex = yindex + 1.5;

		this.doPresets(win, 7, yloc+(gap*yindex), guielements);
		this.doMatrixGUI(win, 180, yloc+(gap*yindex));


		// plank area
		//this.doPlanks(350,yloc-10, 20, 220, 20);


		// pitch detection area
		Button( win, Rect(370,250,80,25))
		.states_([
			["plank detect", Color.white, Color.black],
			["plank detect", Color.black, Color.green]
		])
		.action_({ arg but;
			~plankdetect = but.value.asBoolean;
		}).valueAction_(~plankdetect);

		pitchbuttons.do({ arg item, index;
			pitchbuttons[index] = Button(win, Rect(450+(30*index), 250, 30, 25))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.black, Color.red]
			])
			.action_({ arg butt;
				if (butt.value.asBoolean, {
					~recindex = index;
					pitchbuttons.do({arg bu; bu.value=0});
					butt.value = 1;
				})
			});
		});


		Button(win,  Rect(370, 20,80,25))
		.states_([
			["new set", Color.white, Color.black],
		])
		.action_({ arg butt;
			TxalaSet.new(server, sndpath)
		});

		this.doPlanksSetGUI(win, 370, 280);




		// feddback area

		label = StaticText(win, Rect(370, 375, 250, 60)).font_(Font("Verdana", 25)) ;
		label.string = "BPM: --- \nCompass: ---";

		numbeatslabel = StaticText(win, Rect(370, 440, 250, 25)).font_(Font("Verdana", 25));
		numbeatslabel.string = "Beats: ---";

		hitbutton = Button( win, Rect(370,480,110,55))
		.states_([
			["HIT", Color.white, Color.grey],
			["HIT", Color.white, Color.red]
		]);
		compassbutton = Button( win, Rect(480,480,110,55))
		.states_([
			["PHRASE", Color.white, Color.grey],
			["PHRASE", Color.white, Color.red]
		]);

		hutsunebutton = Button( win, Rect(590,480,100,55))
		.states_([
			["HUTSUN", Color.white, Color.grey],
			["HUTSUN", Color.white, Color.blue]
		]);

		circleanim = TxalaCircleAnim.new(win, 500, 120, 200);

		// I need a task that calls circleanim.scheduleDraw(data)
		// data must be like drawingSet = Array.fill(~buffers.size, {[-1, 0, false, 10]});
		// drawingSet[index] = [currenttemposwing, hittime, txakun, hitamp]; // store for drawing on window.refresh



		win.front;
	}


	/*doPlanks { arg xloc, yloc, gap, width, height;
		var menuxloc = xloc + 44;
		var playxloc = menuxloc+200+2;

		// PLANKS - OHOLAK //////////////////////////////////
		//StaticText(win, Rect(xloc+22, yloc-18, 200, 20)).string = "ER";
		StaticText(win, Rect(menuxloc, yloc-18, 200, 20)).string = "Oholak/Planks";
		//StaticText(win, Rect(menuxloc+230, yloc-16, 200, 20)).string = "% chance";

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

			// if (index==0, {
			// 	planksMenus[index][0].valueAction = 1;
			// 	//planksMenus[index][1].valueAction = 1;
			// });// ONLY activate first ones

			// menus for each plank
			planksMenus[index][1] = PopUpMenu(win,Rect(menuxloc,yloc+(gap*index),200,20))
			.items_( samples.asArray.collect({arg item; PathName.new(item).fileName}) )
			.action_({ arg menu;
				var item = nil;
				try { // when there is no sound for this
					 item = menu.item;
				} {|error|
					"empty slot".postln;
				};

				if (item.isNil.not, {
					~buffers[index] = Buffer.read(server, sndpath ++ menu.item);
				});
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

			// rec buttons row
			Button(win, Rect(playxloc+20,yloc+(gap*index),25,20))
			.states_([
				["rec", Color.red, Color.black],
				["rec", Color.black, Color.red]
			])
			.action_({ arg butt;
				"not working yet!".postln;
			});
		});

	}*/

	updatepresetfiles{arg folder;
		var temp;
		temp = (basepath++"/"++folder++"/*").pathMatch; // update
		temp = temp.asArray.collect({arg item; PathName.new(item).fileName});
		temp = temp.insert(0, "---");
		^temp;
		//presetslisten.asArray.collect({arg item; PathName.new(item).fileName}).insert(0, "---")
	}


	doPresets { arg win, xloc, yloc, guielements;
		var newpreset, popup;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Presets";

		popup = PopUpMenu(win,Rect(xloc,yloc+20,170,20))
		.items_( this.updatepresetfiles("presets_listen") )
		.mouseDownAction_( {arg menu;
			presetslisten = this.updatepresetfiles("presets_listen");
			menu.items = presetslisten;
		} )
		.action_({ arg menu;
			var data;
			("loading..." + basepath ++ "/presets_listen/" ++ menu.item).postln;
			data = Object.readArchive(basepath ++ "/presets_listen/" ++ menu.item);

			if (data.isNil.not, {
				//~answertimecorrection = data[\answertimecorrection];
				~amp = data[\amp];
				//~listenparemeters = data[\amp];
				~gap = data[\gap];
				~gapswing = data[\gapswing];
				~answermode = data[\answermode];
				~hutsunelookup = data[\hutsunelookup];
				~listenparemeters = data[\listenparemeters];

				guielements.gapswing.valueAction = ~gapswing;
				guielements.answermode.valueAction = ~answermode; //menu
				guielements.hutsunelookup.valueAction = ~hutsunelookup;
				guielements.amp.valueAction = ~amp;

				try {
					guielements.gain.valueAction = ~listenparemeters.gain
				}{|err|
					"could not set gain value".postln;
				} ;

				guielements.tempothreshold.valueAction = ~listenparemeters.tempo.threshold;
				guielements.falltime.valueAction = ~listenparemeters.tempo.falltime;
				guielements.checkrate.valueAction = ~listenparemeters.tempo.checkrate;
				guielements.onsetthreshold.valueAction = ~listenparemeters.onset.threshold;
				guielements.relaxtime.valueAction = ~listenparemeters.onset.relaxtime;
				guielements.floor.valueAction = ~listenparemeters.onset.floor;
				guielements.mingap.valueAction = ~listenparemeters.onset.mingap;
			});
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset **
			popup.valueAction_(1);
		}{ |error|
			"no predefined listen preset to be loaded".postln;
			error.postln;
		};

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

			data.put(\amp, ~amp);
			data.put(\listenparemeters, ~listenparemeters);
			data.put(\hutsunelookup, ~hutsunelookup);
			data.put(\gap, ~gap);
			data.put(\gapswing, ~gapswing);
			data.put(\answermode, ~answermode);

			data.writeArchive(basepath ++ "/presets_listen/" ++ filename);

			newpreset.string = ""; //clean field
		});

	}

	doMatrixGUI { arg win, xloc, yloc, guielements;
		var newpreset;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Memory manager";

		yloc = yloc+20;

		Button(win, Rect(xloc,yloc,85,25))
		.states_([
			["learn", Color.white, Color.grey],
			["learn", Color.white, Color.green]
		])
		.action_({ arg butt;
			if (markov.isNil.not, {markov.update = butt.value.asBoolean});
		})
		.valueAction_(1);

		Button(win, Rect(xloc+85,yloc,85,25))
		.states_([
			["reset", Color.white, Color.grey]
		])
		.action_({ arg butt;
			markov.reset();
		});

		yloc = yloc+27;
		PopUpMenu(win,Rect(xloc,yloc,170,20))
		.items_( this.updatepresetfiles("presets_matrix") )
		.mouseDownAction_( { arg menu;
			presetmatrix = this.updatepresetfiles("presets_matrix");
			menu.items = presetmatrix;
		} )
/*		.items_(this.updatepresetfiles("presets_matrix")) //presetmatrix.asArray.collect({arg item; PathName.new(item).fileName}))
		.mouseDownAction_({arg menu;
			presetmatrix = (basepath ++ "/presets_matrix/" ++ "*").pathMatch;
			presetmatrix.insert(0, "---");
			menu.items = presetmatrix.asArray.collect({arg item;
				PathName.new(item).fileName});
		})*/
		.action_({ arg menu;
			var data;
			("loading..." + basepath  ++ "/presets_matrix/" ++  menu.item).postln;
			data = Object.readArchive(basepath  ++ "/presets_matrix/" ++  menu.item);

			try {
				markov.loaddata( data[\beatdata] );
			}{|error|
				("memory is empty?"+error).postln;
			};
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
			try {
				data.put(\beatdata, markov.beatdata2nd);
				data.writeArchive(basepath ++ "/presets_matrix/" ++ filename);
			}{|error|
				("memory is empty?"+error).postln;
			};

			newpreset.string = ""; //clean field
		});
	}




	doPlanksSetGUI { arg win, xloc, yloc;
		var newpreset, popup;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Plank set manager";

		yloc = yloc+20;
		popup = PopUpMenu(win,Rect(xloc,yloc,170,20))
		.items_( this.updatepresetfiles("presets_planks") )
		.mouseDownAction_( { arg menu;
			presetmatrix = this.updatepresetfiles("presets_planks");
			menu.items = presetmatrix;
		} )
		.action_({ arg menu;
			var data;
			("loading..." + basepath  ++ "/presets_planks/" ++  menu.item).postln;
			data = Object.readArchive(basepath  ++ "/presets_planks/" ++  menu.item);
			data.postln;

			this.plankdata = data[\plankdata];

			try {
				txalaonset.plankdata = data[\plankdata]; // this causes error on load because txalaonset is nil yet******
				this.updateTxalaScoreNumPlanks();
			}{|error|
				("not listening yet?"+error).postln;
			};

			this.loadsampleset(menu.item);
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset **
			popup.valueAction_(1);
		}{ |error|
			"no predefined plank preset to be loaded".postln;
			error.postln;
		};

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
			try {
				data.put(\plankdata, txalaonset.plankdata);
				data.writeArchive(basepath ++ "/presets_planks/" ++ filename);
			}{|error|
				("file is empty?"+error).postln;
			};

			newpreset.string = ""; //clean field
		});
	}



	updateTxalaScoreNumPlanks {
		var num = 1;
		try{
			num = this.numactiveplanks()
		}{ |err|
			num = plankdata.size //bad
		};

		if (~txalascore.isNil.not, {~txalascore.updateNumPlanks( num ) });
	}

	numactiveplanks{
		var num = 0;
		plankdata.do({arg arr; // there is a proper way to do this but i cannot be bothered with fighting with the doc system
			if (arr.size.asBoolean, {num=num+1});
		});
		["active planks are", num].postln;
		if (num==0, {num=1}); //score need one line at least
		^num
	}
}
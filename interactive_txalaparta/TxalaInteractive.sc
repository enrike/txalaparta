// txalaparta-like interactive system
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
	var doGUI, label, reset, answer, hutsune, win, scope, <scopesynth;
	var <txalasilence, <txalaonset, lastPattern, patternbank;
	var presetslisten, presetmatrix, basepath, sndpath, <samples,  guielements;
	var planksMenus, hitbutton, compassbutton, prioritybutton, hutsunebutton, numbeatslabel;//, selfcancelation=false;
	var <pitchbuttons, circleanim, drawingSet, >txalacalibration, >txalachroma, <>chromabuttons, makilaanims;
	var answersystems, wchoose, tmarkov, tmarkov2, tmarkov, phrasemode;

	var numplanks = 6; // max planks
	var plankresolution = 5; // max positions per plank
	var ampresolution = 5; // max amps per position. is this num dynamically set?

	*new {| aserver, apath="" |
		^super.new.initTxalaInteractive(aserver, apath);
	}

	initTxalaInteractive { arg aserver, apath;
		server = aserver;
		basepath = apath;
		this.init();
		this.doGUI();
	}

	kill {
		this.stop();
		win.close;
	}

	init {
		~bpm = 60;
		~amp = 1;
		~answer = false;
		~answerpriority = true; // true if answer on group end (sooner), false if answer from group start (later)
		~autoanswerpriority = true;
		~answermode = 1; //0,1,3: imitation, wchoose, ...
		~hutsunelookup = 0.3;
		//~gapswing = 0;
		~latencycorrection = 0.05;
		~learning = true;

		~buffersND = Array.fillND([numplanks, plankresolution], { [] });
		~plankdata = Array.fill(numplanks, {[]});

		drawingSet = [Array.fill(8, {[-1, 0, false, 10]}), Array.fill(8, {[-1, 0, false, 10]})];

		// this is to keep all the values of the listening synths in one place
		~listenparemeters = ().add(\in->0).add(\gain->1);
		~listenparemeters.tempo = ().add(\threshold->0.5).add(\falltime->0.1).add(\checkrate->20);
		~listenparemeters.onset = ().add(\threshold->0.4).add(\relaxtime->0.01).add(\floor->0.1).add(\mingap->1);

		lastPattern = nil;
		phrasemode = 0; // make up a new phrase or imitate a stored one?

		sndpath = basepath ++ "/sounds/";
		samples = (sndpath++"*").pathMatch;
		("sndpath is" + sndpath).postln;
		("available sample sets are" + samples).postln;

		pitchbuttons = Array.fill(~buffersND.size, {nil});
		chromabuttons = Array.fill(numplanks, {nil});

		answersystems = [
			TxalaWChoose.new,
			TxalaMarkov.new,
			TxalaMarkov2.new,
			TxalaMarkov4.new
		];

		patternbank = TxalaPatternBank.new;
		tempocalc = TempoCalculator.new(2);
		~txalascore = TxalaScoreGUI.new;

/*		MIDIClient.init;
		MIDIClient.destinations;
		MIDIIn.connectAll;
		~midiout = MIDIOut(0, MIDIClient.destinations.at(0).uid);*/

		SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
			Out.ar(outbus,
				amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
			)
		}).add;

		this.loadprefsauto();
	}


	loadprefsauto{
		var data;
		("auto loading general preferences ...").postln;
		data = Object.readArchive(basepath ++ "/" ++ "prefs.preset");

		if (data.isNil.not, {
			~latencycorrection = data[\latencycorrection];
			~amp = data[\amp];
			//~gapswing = data[\gapswing];
			~answermode = data[\answermode];
			~learning = data[\learning];
			phrasemode = data[\phrasemode];
		})
	}

	saveprefsauto{
		var data, filename = "prefs.preset";
		data = Dictionary.new;

		data.put(\amp, ~amp);
		//data.put(\gapswing, ~gapswing);
		data.put(\answermode, ~answermode);
		data.put(\latencycorrection, ~latencycorrection);
		data.put(\learning, ~learning);
		data.put(\phrasemode, phrasemode);

		data.writeArchive(basepath ++ "/" ++ filename);
	}

	loadsampleset{ arg presetfilename;
		var foldername = presetfilename.split($.)[0];// get rid of the file extension
		("load sampleset"+foldername).postln;
		~buffersND.do({arg plank, indexplank;
			plank.do({ arg pos, indexpos;
				10.do({ arg indexamp;// this needs to be dynamically calc from the num of samples for that amp
					var filename = "plank" ++ indexplank.asString++indexpos.asString++indexamp.asString++".wav";
					if ( PathName.new(sndpath ++"/"++foldername++"/"++filename).isFile, {
						var tmpbuffer = Buffer.read(server, sndpath ++"/"++foldername++"/"++filename);
						~buffersND[indexplank][indexpos] = ~buffersND[indexplank][indexpos].add(tmpbuffer)
					})
				})
			})
		})
	}


	// SYNTH'S CALLBACKS /////////////////////////////////////////////////////////////////
	// called from silence detection and onset detection synths
	hutsune {
		if (tempocalc.bpms.indexOf(0).isNil, { // wait until it is stable
			lastPattern = ();
			if (~answer, { this.answer(0) }); //asap
			tempocalc.pushlasttime(); // empty hits also count for BPM calc

			if (~txalascore.isNil.not, {
				var last = SystemClock.seconds-((60/~bpm)/2);
				~txalascore.hit(last, -1, 1, 0) ; // -1 for hutsune // detected hutsune on input
			});
			{hutsunebutton.value = 1}.defer; // flash button
			{hutsunebutton.value = 0}.defer(0.2);
		})
	}

	updateGUIstrings {
		{ label.string = "BPM:" + ~bpm + "\nCompass:" + txalasilence.compass}.defer
	}

	broadcastgroupstarted { // silence detection calls this.
		~bpm = tempocalc.calculate();
		this.updateGUIstrings();
		if( (~answer && ~answerpriority.not), { this.answer() }); // schedule BEFORE new phrase ends
		drawingSet = [Array.fill(8, {[-1, 0, false, 10]}), drawingSet[1]]; // prepare red for new data
		{compassbutton.value = 1}.defer;
	}

	broadcastgroupended { // silence detection calls this.
		lastPattern = txalaonset.closegroup(); // to close beat group in the onset detector

		if (lastPattern.isNil.not, {
			{circleanim.scheduleDraw(drawingSet[0], 0)}.defer; // render red asap

			patternbank.addpattern(lastPattern); // store into bank in case it wasnt there
			if( (~answer && ~answerpriority), { this.answer() }); // schedule AFTER new phrase ends
			if (~autoanswerpriority, { this.doautoanswerpriority() });
			if (~txalascore.isNil.not, {
				~txalascore.mark(tempocalc.lasttime, SystemClock.seconds, txalasilence.compass, lastPattern.size)
			});
			{numbeatslabel.string = "Beats:" + lastPattern.size}.defer;
			{compassbutton.value = 0}.defer; // display now
		});
		this.updateGUIstrings();
	}

	newonset { arg hittime, amp, player, plank;
		if (~txalascore.isNil.not, { ~txalascore.hit(hittime, amp, player, plank) });

		if (((txalaonset.curPattern.size-1) < drawingSet[0].size), { // stop drawing if they pile up
			drawingSet[0][txalaonset.curPattern.size-1] = [0, hittime-txalaonset.patternsttime, true, amp] // new red hit
		});

		{hitbutton.value = 1}.defer; // short flash
		{hitbutton.value = 0}.defer(0.055);
	}

	/////////////////////////////////////////////////////////////////////////////

	// activates/deactivates answerpriority if to tight to answer with priority
	doautoanswerpriority {
		var defertime = tempocalc.lasttime + (60/~bpm/2) - SystemClock.seconds;
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
		this.stop();
		txalasilence = TxalaSilenceDetection.new(this, server); // parent, server, mode, answermode
		txalaonset = TxalaOnsetDetection.new(this, server);
		~txalascore.reset();
		tempocalc.reset();
	}

	reset  {
		this.stop();
		this.start();
	}

	answer {arg defertime;
		if ( lastPattern.isNil.not, {
			drawingSet = [drawingSet[0], Array.fill(8, {[-1, 0, false, 10]})]; // prepare blue for new data

			// calc when in future should answer be. start from last detected hit and use tempo to calculate
			// tempocalc.lasttime is when the first hit of the last phrase happened
			if (defertime.isNil, {
				defertime = tempocalc.lasttime + (60/~bpm/2) - SystemClock.seconds - ~latencycorrection;
			});

			if (defertime.isNaN.not, {
				// this is a bit ridiculous. ~answermode is 1,2,3,4 anyway. no?
				switch (~answermode,
					0, { this.imitation(defertime, lastPattern) },
					1, { this.next(defertime, lastPattern.size, 1) },
					2, { this.next(defertime, lastPattern.size, 2) }, // MC 1
					3, { this.next(defertime, lastPattern.size, 3) }, // MC 2
					4, { this.next(defertime, lastPattern.size, 4) }  // MC 4
				);
			});
		})
	}

	imitation { arg defertime, pattern;
		pattern.do({arg hit, index;
			{
				this.playhit(hit.amp, 0, index, pattern.size, hit.plank);
				makilaanims.makilaF(index, 0.15); // prepare anim
			}.defer(defertime + hit.time);
			drawingSet[1][index] = [0, hit.time, false, hit.amp]; // new blue hit
		});

		{circleanim.scheduleDraw(drawingSet[1], 1)}.defer(defertime); // render blue AFTER all hits have been scheduled
	}


	makephrase { arg curhits, defertime;
		var gap=0, hitpattern, lastaverageamp = this.averageamp(); //swingrange,

		// TO DO: should we shorten the gap according to num of curhits?? ******
		// if input is 2 but answer is 4 we cannot use the same gap. needs to be shorter *****
		if (curhits > 1, { gap = this.averagegap() });

		if (curhits==1 && [true, false].wchoose([0.05, 0.95]), { // sometimes play a two hit chord instead of single hit
			gap = 0;
			curhits = 2;
		});

		hitpattern = patternbank.getrandpattern(curhits); // just get any random corresponding to curhits num

		//swingrange = (((60/~bpm)/4)*~gapswing)/100; // calc time from %. max value is half the space for the answer which is half a bar at max. thats why /4

		curhits.do({ arg index;
			var hittime, amp;
			hittime = defertime + (gap * index);// + rrand(swingrange.neg, swingrange);
			amp = lastaverageamp * ~amp; // adapt amplitude to prev detected

			if (this.getaccent, {
				if ((index==0), { amp = amp + rand(0.02, 0.05) });// try to accent first
			}, {
				if ((index==(curhits-1)), { amp = amp + rand(0.02, 0.05) }) // try to accent last;
			});

			if ( hittime.isNaN, { hittime = 0 } );
			if ( hittime == inf, { hittime = 0 } );

			{
				this.playhit( amp, 0, index, curhits, hitpattern.pattern[index].plank );
				makilaanims.makilaF(index, 0.15); // prepare anim
			}.defer(hittime);

			drawingSet[1][index] = [0, hittime-defertime, false, amp]; // append each hit
		});

		{ circleanim.scheduleDraw(drawingSet[1], 1) }.defer(defertime); // render blue
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
		if (val < 0.01, {val = 0.01}); //lower limit
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

	next {arg defertime=0, size=nil, mode=0;
		var curhits = answersystems[mode-1].next(size);

/*		// should we shorten the gap according to num of curhits?? ******
		// if input is 2 but answer is 4 we cannot use the same gap. needs to be shorter *****
		if (curhits > 1, { gap = this.averagegap() });

		if (curhits==1 && [true, false].wchoose([0.2, 0.8]), { // sometimes play a two hit chord instead of single hit
			gap = 0;
			curhits = 2;
		});*/

		if (curhits == 0, { //  we have produced an hutsune
			{
				if (~txalascore.isNil.not, {
					var last = SystemClock.seconds;
					~txalascore.hit(last, -1, 0, 0) ; // -1 for hutsune // machine hutsune
					~txalascore.mark(last, (SystemClock.seconds+defertime), txalasilence.compass, lastPattern.size)
				});
				{hutsunebutton.value = 1}.defer; // flash button
				{hutsunebutton.value = 0}.defer(0.2)
			}.defer(defertime)
		}, {
			if ( defertime < 0, { "oops... running late".postln});

			if (phrasemode.asBoolean.not, { // synth the phrase
				this.makephrase(curhits, defertime)
			},{ // answer with a lick from memory
				var pat = patternbank.getrandpattern(curhits); // just get a previously played pattern
				this.imitation(defertime, pat.pattern); // and imitate it
			});
		});
	}


	playhit { arg amp=0, player=0, index=0, total=0, plank;
		var actualplank, plankpos, plankamp, ranges, positions, choices;

		positions = ~buffersND[plank].copy.takeThese({ arg item; item.size==0 }); // get rid of empty slots. this is not the best way

		// chances of diferent areas depending on number of areas // ugly way to solve it
		if (positions.size==1,{choices = [1]});
		if (positions.size==2,{choices = [0.50, 0.50]});
		if (positions.size==3,{choices = [0.2, 0.65, 0.15]});
		if (positions.size==4,{choices = [0.15, 0.35, 0.35, 0.15]});
		if (positions.size==5,{choices = [0.15, 0.15, 0.3, 0.3, 0.1]});

		// the wchoose needs to be a distribution with more posibilites to happen on center and right
		plankpos = Array.fill(positions.size, {arg n=0; n}).wchoose(choices);

		// which sample corresponds to this amp. careful as each pos might have different num of hits inside
		ranges = Array.fill(~buffersND[plank][plankpos].size, {arg num=0; (1/~buffersND[plank][plankpos].size)*(num+1) });

		plankamp = ranges.detectIndex({arg item; amp<=item});
		if (plankamp.isNil, {plankamp = 1}); // if amp too high it goes nil
		actualplank = ~buffersND[plank][plankpos][plankamp];

		Synth(\playBuf, [\amp, amp, \freq, (1+rrand(-0.003, 0.003)), \bufnum, actualplank.bufnum]);
		if (~txalascore.isNil.not, { ~txalascore.hit(SystemClock.seconds, amp, 0, plank) });

		//~midiout.noteOn(player, plank.bufnum, amp*127);
		// if OSC flag then send OSC out messages here
	}


	/////////////////////////////////// GUI /////////////////////////////////////////////////////////////
	closeGUI {
		win.close();
	}

	doGUI  {
		var yindex=0, yloc = 40, gap=20;

		guielements = ();// to later restore from preferences

		win = Window("Interactive txalaparta by www.ixi-audio.net",  Rect(5, 5, 640, 380));
		win.onClose = {
			this.saveprefsauto();
			if (txalasilence.isNil.not, {txalasilence.kill()});
			if (txalaonset.isNil.not, {txalaonset.kill()});
			if (~txalascore.isNil.not, {~txalascore.close});
			if (txalacalibration.isNil.not, {txalacalibration.close});
			if (txalachroma.isNil.not, {txalachroma.close});
			scopesynth.free;
			scope.free;
		};

		// row of buttons on top side

		Button( win, Rect(5,5,140,38))
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
		Button( win, Rect(5,yloc+3,140,38))
		.states_([
			["answer", Color.white, Color.black],
			["answer", Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		Button( win, Rect(180,5,80,25))
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

		Button(win,  Rect(260,5,80,25))
		.states_([
			["show score", Color.white, Color.black],
		])
		.action_({ arg butt;
			var num = 1;
			try{ num = this.numactiveplanks() };
			~txalascore.doTxalaScore(numactiveplanks:num);
			~txalascore.reset();
		});

		Button( win, Rect(260,yloc-10,40,25)) //Rect(140,30,70,25))
		.states_([
			["scope", Color.white, Color.black],
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


		Button( win, Rect(300,yloc-10,40,25)) //Rect(140,30,70,25))
		.states_([
			["meter", Color.white, Color.black],
		])
		.action_({ arg but;
			server.meter(1,1);
		});

		yindex = yindex + 2.3;

				// mode menu
		StaticText(win, Rect(7, yloc+(gap*yindex)-3, 100, 25)).string = "Answer mode";
		guielements.add(\answermode->
			PopUpMenu(win,Rect(95,yloc+(gap*yindex), 100,20))
			.items_([
				"imitation", // copy exactly what the user does
				"percentage", // just count all the hits and return a wchoose
				"learning 1", // 1sr order markov chain
				"learning 2", // 2nd order markov chain
				"learning 4" // 4th order markov chain
			])
			.action_({ arg menu;
				try{ // bacwrds comp
					~answermode = menu.value.asInt;
					("changing to answer mode:" + menu.item + menu.value).postln;
				}{|err|
					~answermode = 1;
					menu.value = ~answermode;
				}
			})
			.valueAction_(~answermode)
		);

		guielements.add(\amp-> Button(win, Rect(200,yloc+(gap*yindex),125,20))
			.states_([
				["lick from memory", Color.white, Color.grey],
				["lick from memory", Color.white, Color.green]
			])
			.action_({ arg butt;
				phrasemode = butt.value;
			}).value_(phrasemode);

		);

		yindex = yindex + 1;

		// ~amplitude
		guielements.add(\amp-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"volume",
			ControlSpec(0, 1.5, \lin, 0.01, 1, ""),
			{ arg ez;
				~amp = ez.value.asFloat;
			},
			initVal: ~amp,
			labelWidth: 60;
		));

		yindex = yindex + 1;

/*		guielements.add(\gapswing-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"swing",
			ControlSpec(0, 100, \lin, 1, 0, ""),
			{ arg ez;
				~gapswing = ez.value.asFloat;
			},
			initVal: ~gapswing,
			labelWidth: 60;
		));

		yindex = yindex + 1;*/

		guielements.add(\latency-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"latency",
			ControlSpec(0, 0.2, \lin, 0.001, 0, ""),
			{ arg ez;
				~latencycorrection = ez.value.asFloat;
			},
			initVal: ~latencycorrection,
		));

		yindex = yindex + 1.5;

		this.doCalibrationPresets(win, 7, yloc+(gap*yindex), guielements);
		this.doMatrixGUI(win, 180, yloc+(gap*yindex));
		yindex = yindex + 5;
		this.doChromaGUI(win, 7, yloc+(gap*yindex));
		this.doPlanksSetGUI(win, 180, yloc+(gap*yindex));

		// feedback area

		circleanim = TxalaCircle.new(win, 450, 100, 200);

		makilaanims = TxalaSliderAnim.new(win, 550, 10);

		label = StaticText(win, Rect(370, 200, 250, 60)).font_(Font("Verdana", 25)) ;
		label.string = "BPM: --- \nCompass: ---";

		numbeatslabel = StaticText(win, Rect(370, 265, 250, 25)).font_(Font("Verdana", 25));
		numbeatslabel.string = "Beats: ---";

		hitbutton = Button( win, Rect(370,295,90,65))
		.states_([
			["HIT", Color.white, Color.grey],
			["HIT", Color.white, Color.red]
		]);
		compassbutton = Button( win, Rect(460,295,90,65))
		.states_([
			["PHRASE", Color.white, Color.grey],
			["PHRASE", Color.white, Color.red]
		]);

		hutsunebutton = Button( win, Rect(550,295,80,65))
		.states_([
			["HUTSUN", Color.white, Color.grey],
			["HUTSUN", Color.white, Color.green]
		]);

		win.front;
	}


	updatepresetfiles{arg folder;
		var temp;
		temp = (basepath++"/"++folder++"/*").pathMatch; // update
		temp = temp.asArray.collect({arg item; PathName.new(item).fileName});
		temp = temp.insert(0, "---");
		^temp;
	}

	updatesamplesetpresetfiles{
		var temp, names;
		temp = (basepath++"/sounds/*").pathMatch; // update
		names = temp.asArray.collect({arg item;
			var ar = item.split($/);
			ar[ar.size-2]
		});
		names = names.insert(0, "---");
		^names;
	}

	doCalibrationPresets { arg win, xloc, yloc, guielements;
		var newpreset, popup;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Calibration manager";

		Button(win,  Rect(xloc,yloc+20,80,25))
		.states_([
			["edit", Color.white, Color.grey],
		])
		.action_({ arg butt;
			txalacalibration = TxalaCalibration.new(this);
		});

		popup = PopUpMenu(win,Rect(xloc,yloc+47,170,20))
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

				~hutsunelookup = data[\hutsunelookup];
				~listenparemeters = data[\listenparemeters];

				if (txalacalibration.isNil.not, {
					txalacalibration.guielements.hutsunelookup.valueAction = ~hutsunelookup;

					try {
						txalacalibration.guielements.gain.valueAction = ~listenparemeters.gain
					}{|err|
						"could not set gain value".postln;
					} ;

					txalacalibration.guielements.tempothreshold.value = ~listenparemeters.tempo.threshold;
					txalacalibration.guielements.falltime.value = ~listenparemeters.tempo.falltime;
					txalacalibration.guielements.checkrate.value = ~listenparemeters.tempo.checkrate;
					txalacalibration.guielements.onsetthreshold.value = ~listenparemeters.onset.threshold;
					txalacalibration.guielements.relaxtime.value = ~listenparemeters.onset.relaxtime;
					txalacalibration.guielements.floor.value = ~listenparemeters.onset.floor;
					txalacalibration.guielements.mingap.value = ~listenparemeters.onset.mingap;
				});
			});
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset
			popup.valueAction_(1);
		}{ |error|
			"no predefined listen preset to be loaded".postln;
			error.postln;
		};

		newpreset = TextField(win, Rect(xloc, yloc+70, 95, 25));
		Button(win, Rect(xloc+100,yloc+70,70,25))
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
			data.put(\listenparemeters, ~listenparemeters);
			data.put(\hutsunelookup, ~hutsunelookup);
			data.writeArchive(basepath ++ "/presets_listen/" ++ filename);

			newpreset.string = ""; //clean field
		});

	}

	doMatrixGUI { arg win, xloc, yloc, guielements;
		var newpreset;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Memory manager";

		yloc = yloc+20;

		guielements.add(\learning, Button(win, Rect(xloc,yloc,85,25))
		.states_([
			["learn", Color.white, Color.grey],
			["learn", Color.white, Color.green]
		])
		.action_({ arg butt;
			~learning = butt.value.asBoolean;
		}).value_(~learning)
		);

		Button(win, Rect(xloc+85,yloc,85,25))
		.states_([
			["clear", Color.white, Color.grey]
		])
		.action_({ arg butt;
			if (~answermode > 0, {
				answersystems[~answermode-1].reset();
			}, {
				"imitation mode has no memory to reset".postln;
			});
			patternbank.reset();
		});

		yloc = yloc+27;

		PopUpMenu(win,Rect(xloc,yloc,170,20))
		.items_( this.updatepresetfiles("presets_matrix") )
		.mouseDownAction_( { arg menu;
			presetmatrix = this.updatepresetfiles("presets_matrix");
			menu.items = presetmatrix;
		} )
		.action_({ arg menu;
			var data;
			("trying to load..." + basepath  ++ "/presets_matrix/" ++  menu.item).postln;
			data = Object.readArchive(basepath  ++ "/presets_matrix/" ++  menu.item);

			if (~answermode > 0, {
				answersystems[~answermode-1].loaddata( data[\beatdata] );
				patternbank.loaddata( data[\patterndata] );
			}, {
				"imitation mode cannot load memory".postln;
			});
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
			//try {
			if (~answermode > 0, {
				data.put(\beatdata, answersystems[~answermode-1].beatdata);
				data.put(\patterndata, patternbank.bank);
				data.writeArchive(basepath ++ "/presets_matrix/" ++ filename);
			}, {
				"imitation mode cannot load memory".postln;
			});

			newpreset.string = ""; //clean field
		});
	}

	doChromaGUI{arg win, xloc, yloc;
		var newpreset, popup;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Chroma manager";

		chromabuttons.size.do({arg index;
			chromabuttons[index] = Button(win, Rect(xloc+(25*index), yloc+20, 25, 25))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.white, Color.red]
			])
			.action_({ arg butt;
				if (butt.value.asBoolean, {
					~recindex = index;
					chromabuttons.do({arg bu; bu.value=0});//off all
					butt.value = 1;
				}, {
					~recindex = nil
				})
			});

		});

		// the one to delete
		//Button(win, Rect(xloc+(25*(chromabuttons.size+1)), yloc+20, 25, 25))
		Button(win, Rect(xloc+(25*(chromabuttons.size)), yloc+20, 20, 25))
		.states_([
			["C", Color.white, Color.grey]
		])
		.action_({ arg butt;
		    ~plankdata = Array.fill(numplanks, {[]});
			"chromagram data cleared".postln;
		});


		popup = PopUpMenu(win,Rect(xloc,yloc+47,170,20))
		.items_( this.updatepresetfiles("presets_chroma") )
		.mouseDownAction_( {arg menu;
			presetslisten = this.updatepresetfiles("presets_chroma");
			menu.items = presetslisten;
		} )
		.action_({ arg menu;
			var data;
			("loading..." + basepath ++ "/presets_chroma/" ++ menu.item).postln;
			data = Object.readArchive(basepath ++ "/presets_chroma/" ++ menu.item);

			if (data.isNil.not, { ~plankdata = data[\plankdata] });
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset **
			popup.valueAction_(1);
		}{ |error|
			"no predefined chroma preset to be loaded".postln;
			error.postln;
		};

		newpreset = TextField(win, Rect(xloc, yloc+70, 95, 25));
		Button(win, Rect(xloc+100,yloc+70,70,25))
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
			data.put(\plankdata, ~plankdata);
			data.writeArchive(basepath ++ "/presets_chroma/" ++ filename);

			newpreset.string = ""; //clean field
		});
	}


	doPlanksSetGUI { arg win, xloc, yloc;
		var newpreset, popup;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Plank set manager";

		yloc = yloc+20;

		Button(win,  Rect(xloc, yloc,80,25))
		.states_([
			["sample new", Color.white, Color.grey],
		])
		.action_({ arg butt;
			TxalaSet.new(server, sndpath)
		});

		yloc = yloc+27;

		popup = PopUpMenu(win,Rect(xloc,yloc,170,20))
		.items_( this.updatesamplesetpresetfiles() )
		.mouseDownAction_( { arg menu;
			presetmatrix = this.updatesamplesetpresetfiles();
			menu.items = presetmatrix;
		} )
		.action_({ arg menu;
			this.loadsampleset(menu.item);
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset **
			popup.valueAction_(1);
		}{ |error|
			"no predefined plank preset to be loaded".postln;
			error.postln;
		};
	}

	updateTxalaScoreNumPlanks {
		var num = 1;
		try{
			num = this.numactiveplanks()
		}{ |err|
			num = ~plankdata.size //bad
		};

		if (~txalascore.isNil.not, {~txalascore.updateNumPlanks( num ) });
	}

	numactiveplanks{
		var num = 0;
		~plankdata.do({arg arr; // there is a proper way to do this but i cannot be bothered with fighting with the doc system
			if (arr.size.asBoolean, {num=num+1});
		});
		["active planks are", num].postln;
		if (num==0, {num=1}); // one line at least
		^num
	}
}
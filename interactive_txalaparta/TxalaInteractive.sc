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
	var <pitchbuttons, circleanim, drawingSet, >txalacalibration, >txalachroma, <>chromabuttons;
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
		//~plankdata = [[],[],[],[],[],[]];
		this.init();
		this.doGUI();
	}

	init {
		~bpm = 60;
		~amp = 1;
		~answer = false;
		~answerpriority = true; // true if answer on group end (sooner), false if answer from group start (later)
		~autoanswerpriority = true;
		~answermode = 1; //0,1,3: imitation, wchoose, ...
		~hutsunelookup = 0.3;
		//~plankdetect = 1;
		~gapswing = 0;
		~latencycorrection = 0.05;
		~learning = true;

		~buffers = Array.fillND([numplanks, plankresolution], { [] });
		~plankdata = Array.fillND([numplanks, plankresolution], { [] }); // ampresolution??

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
		("available samples are" + samples).postln;

		pitchbuttons = Array.fill(~buffers.size, {nil});
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
			~gapswing = data[\gapswing];
			~answermode = data[\answermode];
			~learning = data[\learning];
		})
	}

	saveprefsauto{
		var data, filename = "prefs.preset";
		data = Dictionary.new;

		data.put(\amp, ~amp);
		data.put(\gapswing, ~gapswing);
		data.put(\answermode, ~answermode);
		data.put(\latencycorrection, ~latencycorrection);
		data.put(\learning, ~learning);

		data.writeArchive(basepath ++ "/" ++ filename);
	}

	loadsampleset{ arg presetfilename;
		var foldername = presetfilename.split($.)[0];// get rid of the file extension
		("load sampleset"+foldername).postln;
		~buffers.do({arg plank, indexplank;
			plank.do({ arg pos, indexpos;
				10.do({ arg indexamp;// this needs to be dynamically calc from the num of samples for that amp
					var filename = "plank" ++ indexplank.asString++indexpos.asString++indexamp.asString++".wav";
					if ( PathName.new(sndpath ++"/"++foldername++"/"++filename).isFile, {
						var tmpbuffer = Buffer.read(server, sndpath ++"/"++foldername++"/"++filename);
						~buffers[indexplank][indexpos] = ~buffers[indexplank][indexpos].add(tmpbuffer)
					})
				})
			})
		})
	}


	// SYNTH'S CALLBACKS /////////////////////////////////////////////////////////////////
	hutsune {
		if (tempocalc.bpms.indexOf(0).isNil, { // wait until it is stable
			lastPattern = ();
			txalasilence.compass =  txalasilence.compass + 1;
			if(~answer, { this.answer(0) }); //asap
			if (~txalascore.isNil.not, {
				var last = SystemClock.seconds-((60/~bpm)/2);
				~txalascore.hit(last, -1, 1, 0) ; // -1 for hutsune
				//~txalascore.mark(last, (last+((60/~bpm)/4)), txalasilence.compass, lastPattern.size)
			});
			tempocalc.pushlasttime(); // empty hits also count for BPM calc
			{hutsunebutton.value = 1}.defer;
			{hutsunebutton.value = 0}.defer(0.2);
		})
	}

	loop {
		{ label.string = "BPM:" + ~bpm + "\nCompass:" + txalasilence.compass}.defer
	}

	broadcastgroupstarted { // silence detection calls this.
		~bpm = tempocalc.calculate();
		if( (~answer && ~answerpriority.not), { this.answer() }); // later
		drawingSet = [Array.fill(8, {[-1, 0, false, 10]}), drawingSet[1]];
		{compassbutton.value = 1}.defer;
	}

	broadcastgroupended { // silence detection calls this.
		lastPattern = txalaonset.closegroup(); // to close beat group in the onset detector

		if (lastPattern.isNil.not, {
			{circleanim.scheduleDraw(drawingSet[0], 0)}.defer; // render asap //
			//drawingSet = [Array.fill(8, {[-1, 0, false, 10]}), drawingSet[1]];

			patternbank.addpattern(lastPattern); // store into bank in case it wasnt there
			if( (~answer && ~answerpriority), { this.answer() }); // asap
			if (~autoanswerpriority, { this.doautoanswerpriority() });
			if (~txalascore.isNil.not, {
				~txalascore.mark(tempocalc.lasttime, SystemClock.seconds, txalasilence.compass, lastPattern.size)
			});
			{numbeatslabel.string = "Beats:" + lastPattern.size}.defer;
			{compassbutton.value = 0}.defer; // display now
		})
	}

	newonset { arg hittime, amp, player, plank;
		if (~txalascore.isNil.not, { ~txalascore.hit(hittime, amp, player, plank) });

		if (((txalaonset.curPattern.size-1) < drawingSet[0].size), { // stop drawing if they pile up
			drawingSet[0][txalaonset.curPattern.size-1] = [0, hittime-txalaonset.patternsttime, true, amp]
		});

		{hitbutton.value = 1}.defer; // short flash
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
			//~plankdata = txalaonset.~plankdata;// will restore itself on new()
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

	answer {arg defertime;
		if ( lastPattern.isNil.not, {
			// calc when in future should answer be. start from last detected hit and use tempo to calculate
			if (defertime.isNil, {
				defertime = tempocalc.lasttime + (60/~bpm/2) - SystemClock.seconds - ~latencycorrection;
			});

			if (defertime.isNaN.not, {
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
				drawingSet[1][index] = [0, (defertime + hit.time), false, hit.amp]; // append each hit
			}.defer(defertime + hit.time);
		});
	}


	makephrase { arg curhits, defertime;
		var gap=0, hitpattern, swingrange, lastaverageamp = this.averageamp();

		// should we shorten the gap according to num of curhits?? ******
		// if input is 2 but answer is 4 we cannot use the same gap. needs to be shorter *****
		if (curhits > 1, { gap = this.averagegap() });

		if (curhits==1 && [true, false].wchoose([0.2, 0.8]), { // sometimes play a two hit chord instead of single hit
			gap = 0;
			curhits = 2;
		});

		hitpattern = patternbank.getrandpattern(curhits); // just get any random corresponding to curhits num

		swingrange = (((60/~bpm)/4)*~gapswing)/100; // calc time from %. max value is half the space for the answer which is half a bar at max. thats why /4

		curhits.do({ arg index;
			var hittime, amp;
			hittime = defertime + (gap * index) + rrand(swingrange.neg, swingrange);
			amp = (lastaverageamp + rrand(-0.05, 0.05)) * ~amp; // adapt amplitude to prev detected

			if (this.getaccent, {
				if ((index==0), { amp = amp + rand(0.02, 0.05) });// accent first
				}, {
					if ((index==(curhits-1)), { amp = amp + rand(0.02, 0.05) }) // accent last;
			});

			if ( hittime.isNaN, { hittime = 0 } );
			if ( hittime == inf, { hittime = 0 } );

			{ this.playhit( amp, 0, index, curhits, hitpattern.pattern[index].plank) }.defer(hittime);
			drawingSet[1][index] = [0, (hittime-defertime), false, amp]; // append each hit
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

		if (curhits == 0, { // hutsune
			{
				if (~txalascore.isNil.not, {
					var last = SystemClock.seconds;
					~txalascore.hit(last, -1, 1, 0) ; // -1 for hutsune
					~txalascore.mark(last, (SystemClock.seconds+defertime), txalasilence.compass, lastPattern.size)
				});
				{hutsunebutton.value = 1}.defer;
				{hutsunebutton.value = 0}.defer(0.2)
			}.defer(defertime)

		}, {

			drawingSet = [drawingSet[0], Array.fill(8, {[-1, 0, false, 10]})];

			if ( defertime < 0, {
				"TOO LATE TO ANSWER!!!!".postln;
			}); // to late to answer properly?


			if (phrasemode.asBoolean.not, { // synth the phrase
				this.makephrase(curhits, defertime)
			},{ // slack
				var pat = patternbank.getrandpattern(curhits);
				this.imitation(defertime, pat.pattern);
			});

/*			hitpattern = patternbank.getrandpattern(curhits); // just get any random corresponding to curhits num

			swingrange = (((60/~bpm)/4)*~gapswing)/100; // calc time from %. max value is half the space for the answer which is half a bar at max. thats why /4

			curhits.do({ arg index;
				var hittime, amp;
				hittime = defertime + (gap * index) + rrand(swingrange.neg, swingrange);
				amp = (lastaverageamp + rrand(-0.05, 0.05)) * ~amp; // adapt amplitude to prev detected

				if (this.getaccent, {
					if ((index==0), { amp = amp + rand(0.02, 0.05) });// accent first
				}, {
					if ((index==(curhits-1)), { amp = amp + rand(0.02, 0.05) }) // accent last;
				});

				if ( hittime.isNaN, { hittime = 0 } );
				if ( hittime == inf, { hittime = 0 } );

				{ this.playhit( amp, 0, index, curhits, hitpattern.pattern[index].plank) }.defer(hittime);
				drawingSet[1][index] = [0, (hittime-defertime), false, amp]; // append each hit
			});*/

		// THIS NEEDS THE GAP
		//	{ circleanim.scheduleDraw(drawingSet[1], 1) }.defer(defertime + (gap * (curhits.size-1))); // schedule with last hit
		});
	}

/*	selfcancel { arg plank, index, total;
		if (selfcancelation, { // dont listen while I am playing myself
			if (index==0, { this.processflag(true) });

			if ((index==(total-1)), { // listen again when the last hit stops
				var hitlength = plank.numFrames/plank.sampleRate;
				hitlength = hitlength * 0.4; // but remove the sound tail. expose this in the GUI?
				{ this.processflag(false) }.defer(hitlength)
			});
		});
	}*/

	playhit { arg amp=0, player=0, index=0, total=0, plank;
		var actualplank, plankpos, plankamp, ranges, positions, choices;
		//this.selfcancel(plank, index, total); // only if enabled by user

		positions = ~buffers[plank].copy.takeThese({ arg item; item.size==0 });// get rid of empty slots. this is not the best way

		if (positions.size==1,{choices = [1]}); // ugly way to solve it
		if (positions.size==2,{choices = [0.50, 0.50]});
		if (positions.size==3,{choices = [0.2, 0.65, 0.15]});
		if (positions.size==4,{choices = [0.15, 0.35, 0.35, 0.15]});
		if (positions.size==5,{choices = [0.15, 0.15, 0.3, 0.3, 0.1]});

		// the wchoose needs to be a distribution with more posibilites to happen on center and right
		plankpos = Array.fill(positions.size, {arg n=0; n}).wchoose(choices); // focus on plank center

		// which sample corresponds to this amp. careful as each pos might have different num of hits inside
		ranges = Array.fill(~buffers[plank][plankpos].size, {arg num=0; (1/~buffers[plank][plankpos].size)*(num+1) });
		plankamp = ranges.detectIndex({arg item; amp<=item});
		actualplank = ~buffers[plank][plankpos][plankamp];

		Synth(\playBuf, [\amp, amp, \freq, (1+rrand(-0.003, 0.003)), \bufnum, actualplank.bufnum]);
		if (~txalascore.isNil.not, { ~txalascore.hit(SystemClock.seconds, amp, 0, plank) });

		//~midiout.noteOn(player, plank.bufnum, amp*127);
		// if OSC flag then send OSC out messages here
	}

	closeGUI {
		win.close();
	}

	doGUI  {
		var yindex=0, yloc = 40, gap=20; //Array.fill(10, {nil});

		guielements = ();// to later restore from preferences

		win = Window("Interactive txalaparta by www.ixi-audio.net",  Rect(5, 5, 700, 380));
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

/*		Button( win, Rect(180,yloc+15,80,25))
		.states_([
			["self-cancel", Color.white, Color.black],
			["cancel me", Color.black, Color.red]
		])
		.action_({ arg but;
			selfcancelation = but.value.asBoolean;
		});*/

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

		Button(win, Rect(200,yloc+(gap*yindex)-3,55,20))
		.states_([
			["slack", Color.white, Color.grey],
			["slack", Color.white, Color.green]
		])
		.action_({ arg butt;
			phrasemode = butt.value;
		}).value_(phrasemode);

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

		yindex = yindex + 1;

		guielements.add(\gapswing-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"swing",
			ControlSpec(0, 100, \lin, 1, 0, ""),
			{ arg ez;
				~gapswing = ez.value.asFloat;
			},
			initVal: ~gapswing,
			labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\latency-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"latency",
			ControlSpec(0, 0.2, \lin, 0.01, 0, ""),
			{ arg ez;
				~latencycorrection = ez.value.asFloat;
			},
			initVal: ~latencycorrection,
			labelWidth: 60;
		));

		yindex = yindex + 1.5;

		this.doCalibrationPresets(win, 7, yloc+(gap*yindex), guielements);
		this.doMatrixGUI(win, 180, yloc+(gap*yindex));
		yindex = yindex + 5;
		this.doChromaGUI(win, 7, yloc+(gap*yindex));
		this.doPlanksSetGUI(win, 180, yloc+(gap*yindex));

		// feddback area

		circleanim = TxalaCircle.new(win, 450, 100, 200);

		label = StaticText(win, Rect(370, 200, 250, 60)).font_(Font("Verdana", 25)) ;
		label.string = "BPM: --- \nCompass: ---";

		numbeatslabel = StaticText(win, Rect(370, 265, 250, 25)).font_(Font("Verdana", 25));
		numbeatslabel.string = "Beats: ---";

		hitbutton = Button( win, Rect(370,295,110,55))
		.states_([
			["HIT", Color.white, Color.grey],
			["HIT", Color.white, Color.red]
		]);
		compassbutton = Button( win, Rect(480,295,110,55))
		.states_([
			["PHRASE", Color.white, Color.grey],
			["PHRASE", Color.white, Color.red]
		]);

		hutsunebutton = Button( win, Rect(590,295,100,55))
		.states_([
			["HUTSUN", Color.white, Color.grey],
			["HUTSUN", Color.white, Color.blue]
		]);

		win.front;
	}


	updatepresetfiles{arg folder;
		var temp;
		temp = (basepath++"/"++folder++"/*").pathMatch; // update
		temp = temp.asArray.collect({arg item; PathName.new(item).fileName});
		temp = temp.insert(0, "---");
		^temp;
		//presetslisten.asArray.collect({arg item; PathName.new(item).fileName}).insert(0, "---")
	}

	updatesamplesetpresetfiles{
		var temp, names;
		temp = (basepath++"/sounds/*").pathMatch; // update
		names = temp.asArray.collect({arg item;
			var ar = item.split($/);
			ar[ar.size-2]
		});
		//names.postln;
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
					//txalacalibration.guielements.postln;
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
		try{ // AUTO load first preset **
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
			["reset", Color.white, Color.grey]
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
			//}{|error|
			//	("memory is empty?"+error).postln;
			//};

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

			if (data.isNil.not, { ~plankdata = data[\plankdata]; ~plankdata.postln });
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
			var data, dirname;
			//dirname = menu.item.split($/)[0];
			//("loading..." + dirname).postln;
			//data = Object.readArchive(basepath ++ "/sounds/" ++ dirname ++ "/chromagram.preset");
			//~plankdata = data[\plankdata];

/*			try {
				this.updateTxalaScoreNumPlanks();
			}{|error|
				("not listening yet?"+error).postln;
			};*/

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
		if (num==0, {num=1}); //score need one line at least
		^num
	}
}
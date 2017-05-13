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
	var doGUI, label, win, scope, <scopesynth;
	var <txalasilence, <txalaonset, lastPattern, patternbank;
	var presetslisten, basepath, sndpath, <samples,  guielements;
	var planksMenus, hitbutton, compassbutton, prioritybutton, hutsunebutton, numbeatslabel;//, selfcancelation=false;
	var <pitchbuttons, circleanim, drawingSet, >txalacalibration, >txalachroma, <>chromabuttons, makilaanims;
	var answersystems, wchoose, tmarkov, tmarkov2, tmarkov, lastgap, lastamp; //phrasemode

	var numplanks = 6; // max planks
	var plankresolution = 5; // max positions per plank
	var ampresolution = 5; // max amps per position. is this num dynamically set?

	var listeningDisplay;

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
		~answerpriority = false; // true if answer on group end (sooner), false if answer from group start (later)
		~autoanswerpriority = true;
		~answermode = 1; //0,1,3: imitation, wchoose, ...
		~timedivision = 50; // %
		~hutsunelookup = 1; //0.4;
		//~gapswing = 0;
		~latencycorrection = 0.05;
		~learning = true;

		~buffersND = Array.fillND([numplanks, plankresolution], { [] });
		~plankdata = Array.fill(numplanks, {[]});

		drawingSet = [Array.fill(8, {[-1, 0, false, 10]}), Array.fill(8, {[-1, 0, false, 10]})];

		// this is to keep all the values of the listening synths in one place
		~listenparemeters = ().add(\in->0).add(\gain->1);
		~listenparemeters.tempo = ().add(\threshold->0.5).add(\falltime->0.18).add(\checkrate->30).add(\comp_thres->0.3);
		//~listenparemeters = ().add(\in->0).add(\gain->1.3);
		//~listenparemeters.tempo = ().add(\threshold->0.1).add(\falltime->0.2).add(\checkrate->30).add(\comp_thres->0.1);
		~listenparemeters.onset = ().add(\threshold->0.4).add(\relaxtime->0.01).add(\floor->0.05).add(\mingap->1);

		lastPattern = nil;
		//phrasemode = 0; // make up a new phrase or imitate a stored one?
		lastgap = 0;
		lastamp = 0;

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
		{ this.reset() }.defer(0.2); //start listening
	}


	loadprefsauto{
		var data;
		("auto loading general preferences ...").postln;
		data = Object.readArchive(basepath ++ "/" ++ "prefs.preset");

		if (data.isNil.not, {
			~latencycorrection = data[\latencycorrection];
			~amp = data[\amp];
			~hutsunelookup = data[\hutsune];
			~answermode = data[\answermode];
			~learning = data[\learning];
			~timedivision = data[\timedivision];
			//phrasemode = data[\phrasemode];
		})
	}

	saveprefsauto{
		var data, filename = "prefs.preset";
		data = Dictionary.new;

		data.put(\amp, ~amp);
		data.put(\hutsune, ~hutsunelookup);
		data.put(\answermode, ~answermode);
		data.put(\latencycorrection, ~latencycorrection);
		data.put(\timedivision, ~timedivision);
		data.put(\learning, ~learning);
		//data.put(\phrasemode, phrasemode);

		data.writeArchive(basepath ++ "/" ++ filename);
	}

	loadsampleset{ arg presetfilename;
		var foldername = presetfilename.split($.)[0];// get rid of the file extension
		("load sampleset"+foldername).postln;
		~buffersND = Array.fillND([numplanks, plankresolution], { [] }); // clean first
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
				var last = SystemClock.seconds-((60/~bpm)/(100/~timedivision));
				~txalascore.hit(last, -1, 1, 0) ; // -1 for hutsune // detected hutsune on input
			});
			{hutsunebutton.value = 1}.defer; // flash button
			{hutsunebutton.value = 0}.defer(0.2);
		})
	}

	updateGUIstrings {
		{ label.string = ~txl.do("Compass:") + txalasilence.compass + "\nBPM:" + ~bpm }.defer
	}

	broadcastgroupstarted { // silence detection calls this.
		~bpm = tempocalc.calculate();
		this.updateGUIstrings();
		if( (~answer && ~answerpriority.not), { this.answer() }); // schedule BEFORE new group ends
		drawingSet = [Array.fill(8, {[-1, 0, false, 10]}), drawingSet[1]]; // prepare red for new data
		{compassbutton.value = 1}.defer;
	}

	broadcastgroupended { // silence detection calls this.
		lastPattern = txalaonset.closegroup(); // to close beat group in the onset detector

		if (lastPattern.isNil.not, {
			if( (~answer && ~answerpriority), { this.answer() }); // schedule AFTER new group ends
			//if (~autoanswerpriority, { this.doautoanswerpriority() });

			{circleanim.scheduleDraw(drawingSet[0], 0)}.defer; // render red asap
			patternbank.addpattern(lastPattern); // store into bank in case it wasnt there);

			if (~txalascore.isNil.not, {
				~txalascore.mark(tempocalc.lasttime, SystemClock.seconds, txalasilence.compass, lastPattern.size)
			});
			{numbeatslabel.string = ~txl.do("Beats:") + lastPattern.size}.defer;
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
	/*doautoanswerpriority {
		var defertime = tempocalc.lasttime + (60/~bpm/(100/~timedivision)) - SystemClock.seconds;
		~answerpriority = defertime > 0;
		{ prioritybutton.value = ~answerpriority.asInt }.defer;
	}*/

	stop {
		{listeningDisplay.string = ~txl.do("stoping...")}.defer;
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
		{listeningDisplay.string = ~txl.do("listening...")}.defer;
		{
			txalasilence = TxalaSilenceDetection.new(this, server); // parent, server, mode, answermode
			txalaonset = TxalaOnsetDetection.new(this, server);
			~txalascore.reset();
			tempocalc.reset();
		}.defer(0.1); // just in case. givin the server some time for reseting
	}

	reset  {
		"reseting".postln;
		this.stop();
		this.start();
	}

	answer {arg defertime;
		if ( lastPattern.isNil.not, {
			drawingSet = [drawingSet[0], Array.fill(8, {[-1, 0, false, 10]})]; // prepare blue for new data
			// calc when in future should answer be. start from last detected hit and use tempo to calculate
			if (defertime.isNil, {
				defertime = tempocalc.lasttime + (60/~bpm/(100/~timedivision)) - SystemClock.seconds - ~latencycorrection;
			});

			if (defertime.isNaN.not, {
				switch (~answermode,
					0, { this.imitation(defertime, lastPattern) }, // imitation
				//	1, { this.next(defertime, 1) }, // average. not used any longer
					1, { this.next(defertime, 2) }, // MC 1
					2, { this.next(defertime, 3) }, // MC 2
					3, { this.next(defertime, 4) }  // MC 4
				);
			});
		})
	}

	// exact imitation
	imitation { arg defertime, pattern, strech = 1, amp=1;
		pattern.do({arg hit, index;
			{
				this.playhit(hit.amp*amp, 0, index, pattern.size, hit.plank);
				makilaanims.makilaF(index, 0.15); // prepare anim
			}.defer(defertime + (hit.time*strech));
			drawingSet[1][index] = [0, hit.time*strech, false, hit.amp]; // new blue hit
		});

		{circleanim.scheduleDraw(drawingSet[1], 1)}.defer(defertime); // render blue when they are about to play
	}

	// imitates adapting to current gap size and amp
	reproduce {arg defertime, pattern;
		var strech = 1, amp = 1;

		amp = this.averageamp() * ~amp; // adapt amplitude to prev detected

		if (pattern.size>1, { // strech pattern according to current gap
			var gap, mygap;
			if (lastPattern.size == 1, { // when answering to a single hit be careful with strech
				gap = this.calcgap(pattern.size);
			},{
				gap = this.averagegap();
			});
			mygap = pattern.last.time / (pattern.size-1);
			strech = gap / mygap;

			// adapt calibration to gap aperture here. raise as gap increases
			//[this.averageamp()*5, gap*4].postln;
			//~listenparemeters.tempo.falltime = gap * 4;
			//~listenparemeters.tempo.threshold = this.averageamp() * 5;
		});
		this.imitation(defertime, pattern, strech, amp);
	}

	/*
	makephrase { arg curhits, defertime;
		var gap=0, hitpattern, lastaverageamp = this.averageamp(); //swingrange,

		gap = this.averagegap();

		if (curhits==1 && [true, false].wchoose([0.05, 0.95]), { // sometimes play a two hit chord instead of single hit
			gap = 0;
			curhits = 2;
		});

		hitpattern = patternbank.getrandpattern(curhits); // just get any random corresponding to curhits num

		curhits.do({ arg index;
			var hittime, amp;
			hittime = defertime + (gap * index);
			amp = lastaverageamp * ~amp; // adapt amplitude to prev detected

			if (this.getaccent(), {
				if (index==0, { amp = amp + rand(0.02, 0.05) }) // try to accent first
			}, {
				if (index==(curhits-1), { amp = amp + rand(0.02, 0.05) }) // try to accent last
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
	}*/

	// analysing of lastPattern
	averageamp { // returns average amp from hits in last group
		var val=0;
		if (lastPattern.size>0, {
			lastPattern.do({ arg hit;
				val = val + hit.amp;
			});
			val = val/(lastPattern.size);
		}, {
			val = lastamp;
		});
		lastamp = val;
		^val;
	}

	averagegap { // returns average gap time between hits in last group
		var val=0;
		//lastPattern.size.postln;
		if (lastPattern.size > 1, {
			lastPattern.do({ arg hit, index;
				if (index > 0, { //sum all gaps
					val = val + (hit.time-lastPattern[index-1].time);
				});
			});
			val = val / (lastPattern.size-1); // num of gaps is num of hits-1
		}, {
			val = lastgap;
		});
		if (val < 0.007, {val = 0.007}); //lower limit
		lastgap = val;
		^val;
	}

	// manually calculate how long should be the gap between hits for the current situation
	calcgap { arg numhits;
		var val = (( 60 / ~bpm ) / 2 ) / numhits;
		^val;
	}

	getaccent{ // check if first or last hit are accentuated. true 1st / false other
		var res;
		if (lastPattern.size > 0, {
			res = (lastPattern.first.amp >= lastPattern.last.amp);
		},{
			res = true;
		});
		^res
	}

	next {arg defertime=0, mode=0;
		var curhits = answersystems[mode-1].next(lastPattern.size); // decide number of hits in answer
		var pat;

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
			if ( defertime < 0, { "answering late!".postln});

			//if (phrasemode.asBoolean.not, { // create the answer "synthetically"
			//	this.makephrase(curhits, defertime)
			//},{ // answer with a lick from memory
				pat = patternbank.getrandpattern(curhits); // just get a previously played pattern
				this.reproduce(defertime, pat.pattern); // and imitate it
			//});
		});
	}


	playhit { arg amp=0, player=0, index=0, total=0, plank;
		var actualplank, plankpos, plankamp, ranges, positions, choices;

		positions = ~buffersND[plank].copy.takeThese({ arg item; item.size==0 }); // get rid of empty slots. this is not the best way

		choices = [ [1], [0.50, 0.50], [0.2, 0.65, 0.15], [0.15, 0.35, 0.35, 0.15], [0.15, 0.15, 0.3, 0.3, 0.1]]; // chances to play in different areas of the plank according to samples available

		// the wchoose needs to be a distribution with more posibilites to happen on center and right
		plankpos = Array.fill(positions.size, {arg n=0; n}).wchoose(choices[positions.size-1]);

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

		win = Window(~txl.do("Interactive txalaparta by www.ixi-audio.net"),  Rect(5, 5, 640, 430));
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

		Button( win, Rect(5,5,160,38))
		.states_([
			[~txl.do("answer"), Color.white, Color.black],
			[~txl.do("answer"), Color.black, Color.green],
		])
		.action_({ arg but;
			~answer = but.value.asBoolean;
		});

		{listeningDisplay = StaticText(win, Rect(5, yloc+10, 80, 25)).string = ~txl.do("listening...")}.defer;


		Button( win, Rect(85,yloc+3,80,38))
		.states_([
			[~txl.do("reset"), Color.white, Color.grey]
		])
		.action_({ arg but;
			this.reset();
		});

		Button(win,  Rect(225,5,115,38))
		.states_([
			[~txl.do("show score"), Color.white, Color.grey],
		])
		.action_({ arg butt;
			var num = 1;
			try{ num = this.numactiveplanks() };
			~txalascore.doTxalaScore(numactiveplanks:num);
			~txalascore.reset();
		});

		Button(win,  Rect(225,yloc+3,115,38))
		.states_([
			[~txl.do("calibration"), Color.white, Color.grey],
		])
		.action_({ arg butt;
			txalacalibration = TxalaCalibration.new(this, basepath);
		});

		yindex = yindex + 2.3;

		// mode menu
		StaticText(win, Rect(7, yloc+(gap*yindex)-3, 140, 25)).string = ~txl.do("answer mode");
		guielements.add(\answermode->
			PopUpMenu(win,Rect(130,yloc+(gap*yindex), 160,20))
			.items_([
				~txl.do("imitation"), // copy exactly what the user does
				//~txl.do("percentage"), // just count all the hits and return a wchoose
				~txl.do("memory"), // 1sr order markov chain
				~txl.do("memory 1 bar"), // 2nd order markov chain
				~txl.do("memory 2 bars") // 4th order markov chain
			])
			.action_({ arg menu;
				try{ // bacwrds comp
					~answermode = menu.value.asInt;
					(~txl.do("changing to answer mode:") + menu.item + menu.value).postln;
				}{|err|
					~answermode = 1;
					menu.value = ~answermode;
				}
			})
			.valueAction_(~answermode)
		);

		yindex = yindex + 1.2;

		// ~amplitude
		guielements.add(\amp-> EZSlider( win,
			Rect(10,yloc+(gap*yindex),340,20),
			~txl.do("volume"),
			ControlSpec(0, 3, \lin, 0.01, 1, ""),
			{ arg ez;
				~amp = ez.value.asFloat;
			},
			initVal: ~amp,
			labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\latency-> EZSlider( win,
			Rect(10,yloc+(gap*yindex),340,20),
			~txl.do("latency"),
			ControlSpec(0, 0.5, \lin, 0.001, 0, ""),
			{ arg ez;
				~latencycorrection = ez.value.asFloat;
			},
			initVal: ~latencycorrection,
		));

		yindex = yindex + 1.5;


		guielements.add(\hutsune-> Button(win, Rect(10,160,135,35))
			.states_([
				[~txl.do("detect hutsune"), Color.white, Color.black],
				[~txl.do("detect hutsune"), Color.black, Color.green],
			])
			.action_({ arg butt;
				~hutsunelookup = butt.value;
			})
			.valueAction_(~hutsunelookup);
		);


		ServerMeterView(server, win, 10@200, 2, 2); // IN/OUT METERS

		this.doMatrixGUI(win, 165, yloc+(gap*yindex));
		yindex = yindex + 5;
		this.doChromaGUI(win, 165, yloc+(gap*yindex));

		yindex = yindex + 5;

		this.doPlanksSetGUI(win, 165, yloc+(gap*yindex));

		// feedback area
		circleanim = TxalaCircle.new(win, 450, 100, 200);
		makilaanims = TxalaSliderAnim.new(win, 550, 10);

		label = StaticText(win, Rect(370, 200, 250, 60)).font_(Font("Verdana", 25)) ;
		//label.string = "Compass: --- \nBPM: ---";
		label.string = ~txl.do("Compass:") + "" + "\nBPM:" + "";

		numbeatslabel = StaticText(win, Rect(370, 265, 250, 30)).font_(Font("Verdana", 25));
		numbeatslabel.string = ~txl.do("Beats:");

		hitbutton = Button( win, Rect(370,300,90,65))
		.states_([
			[~txl.do("HIT"), Color.white, Color.grey],
			[~txl.do("HIT"), Color.white, Color.red]
		]);
		compassbutton = Button( win, Rect(460,300,90,65))
		.states_([
			[~txl.do("PHRASE"), Color.white, Color.grey],
			[~txl.do("PHRASE"), Color.white, Color.red]
		]);

		hutsunebutton = Button( win, Rect(550,300,80,65))
		.states_([
			["HUTSUN", Color.white, Color.grey],
			["HUTSUN", Color.white, Color.green]
		]);

		win.front;

		TxalaCalibration.new(this, basepath).close;// this is a hack to load the default preset
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
			var ar;
			Platform.case(
				\windows, {item = item.replace("\\", "/")}
			);
			ar = item.split($/);
			ar[ar.size-2]
		});
		names = names.insert(0, "---");
		^names;
	}

	doMatrixGUI { arg win, xloc, yloc, guielements;
		var newpreset;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = ~txl.do("Memory manager");

		yloc = yloc+20;

		guielements.add(\learning, Button(win, Rect(xloc,yloc,85,25))
		.states_([
				[~txl.do("learn"), Color.white, Color.grey],
				[~txl.do("learn"), Color.white, Color.green]
		])
		.action_({ arg butt;
			~learning = butt.value.asBoolean;
		}).value_(~learning)
		);

		Button(win, Rect(xloc+85,yloc,85,25))
		.states_([
			[~txl.do("clear"), Color.white, Color.grey]
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
			menu.items = this.updatepresetfiles("presets_matrix");
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
			[~txl.do("save"), Color.white, Color.grey]
		])
		.action_({ arg butt;
			var filename, data;
			if (newpreset.string == "",
				{filename = Date.getDate.stamp++".preset"},
				{filename = newpreset.string++".preset"}
			);

			data = Dictionary.new;
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

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = ~txl.do("Chroma manager");

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
			this.updateTxalaScoreNumPlanks();
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
			[~txl.do("save"), Color.white, Color.grey]
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

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = ~txl.do("Plank set manager");

		yloc = yloc+20;

		popup = PopUpMenu(win,Rect(xloc,yloc,170,20))
		.items_( this.updatesamplesetpresetfiles() )
		.mouseDownAction_( { arg menu;
			menu.items = this.updatesamplesetpresetfiles();
		} )
		.action_({ arg menu;
			this.loadsampleset(menu.item);
			this.updateTxalaScoreNumPlanks();
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset **
			popup.valueAction_(1);
		}{ |error|
			"no predefined plank preset to be loaded".postln;
			error.postln;
		};

		yloc = yloc+23;

		Button(win,  Rect(xloc, yloc,100,25))
		.states_([
			[~txl.do("sample new"), Color.white, Color.grey],
		])
		.action_({ arg butt;
			TxalaSet.new(server, sndpath, ~listenparemeters, basepath)
		});
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
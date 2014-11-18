/*
txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta


this is a list that tries to summarise all possible controls for a digital txalaparta

* setup parameters
Tables
number of tables
type of wood of each table
table’s shape (length, width, height)
table’s pitch. harmonics or just partials? main freq?
allow metal tubes? (Tobera)

Sticks
type of wood of sticks
length of sticks (useless?)
shape of sticks (useless?)

Supports
position of supports within the table
amount of vibration allowed by supports (different materials used)

Music modes
Old txalaparta (one table, only partials, txakun does not change)
New txalaparta
Newest txalaparta (tuned, one harmonic per table, emphasis on second beat, allows for 3 and 4 hits per beat,          ritmos ternarios y cuaternarios)

Limits
Max amplitude
Min/Max tempo (distance between beats)

* performance parameters
Time
overall tempo: time between players + swing
time between hits of a beat of each player + swing

Rhythm
Number of hits by each player on a beat: 0, 1, 2 (txakun), 3 (txukutun), 4 (txakata)

Dynamics:
general amplitude for both players
emphasis on first or second hit? (forte/piano or piano/forte)
amplitude offset of each beat from general amplitude
amplitude offset of each hit from its beat amplitude

Timbre
table being hit (per hit)
location of each hit within the table’s length
pitch (per hit)

Pan (stereo)
hits could be pan along the planks
*/

/*
Ideas para supercollider txalaparta :
- assign enter down to play button
- double check OSC out
- expose the weight of the chance for the beats (per player?)
- add and expose the weight of the chance for the planks (per player as well?) oier suggested
- añadir sistema de toque interactivo (persona + máquina)
- incorporar escucha (en el caso de persona + máquina)
- incorporar memoria (propia y del otro)
- allow pan?
- should pan be mapped to the length of the virtual planks? so that as the hit moves along the plank the pan changes but also the filter affects the sound.
*/



/*
s.meter
s.makeGui
s.scope(2)
s.freqscope

s.prepareForRecord (path)
s.record(path)
s.stopRecording

s.volume = -20 //-90 <> 5.5
s.mute
s.unmute
*/


Server.default = Server.internal; // this is to avoid problem in some windows machines
s.boot; //////// BOOT SERVER //////////////////

s.doWhenBooted({

	var playF, makilaF, txalascoreF, dohits, dohitsold, getplankindex, getnumactiveplanks, presetspath, drawingSet, drawingSetB, netadd, scoreArray, midioutdev, startTime;

	// GUI vars
	var window, timelinewin, clock, output, slidersauto, makilasliders, nextautopilot, sndpath, samples, buffers, istheresomething, findIndex, presets, tscore;
	// GUI widgets
	var sliders, beatbuttons, beatsliders, oldpulseBut, emphasisBut, zerolimitBut, pulseBut, planksMenus, ampBut, playBut, enabledButs;
	// GUI functions vars
	var doWindow, doMakilas, doTimeControls, doButtons, doPlanks, doPresets, doTxalaScore, scheduleDraw, postOutput, doControPanel, drawHitSet, updateTxalaScoreNumPlanks;

	"STARTING TXALAPARTA !!!!!!!".postln;

	// GLOBAL vars
	~tempo = 70; // tempo. txakun / errena
	~swing = 0.1;
	~gapswing = 0.1;
	~gap = 0.22; // between hits. in txalaparta berria all gaps are more equal
	~amp = 0.5;
	//~classictxakun = true; // in txalaparta zaharra the txakun always 2 hits
	~pulse = false; // should we keep stedy pulse in the tempo or not?
	~freqs = [230, 231]; //
	~lastemphasis = true; // which one is stronger. actualy just using first or last
	~zerolimit = true; //allow 0 more than once or not?
	~enabled = [true, true]; // switch on/off txakun and errena
	//~allowedbeats = [0, 1, 2, nil, nil]; // 0,1,2,3,4
	~allowedbeats = [[0, 1, 2, nil, nil], [0, 1, 2, nil, nil]];
	~beatchance = [0.15, 0.25, 0.35, 0.15, 0.1];
	~plankchance = (Array.fill(8, {1}));
	~autopilotrange = [5, 10]; // for instance
	~mode = true; // old style hit position calulation?
	~oscout = false;
	~midiout = false;// not yet

	// utility
	~verbose = 1;

	scoreArray = []; //stores all events as a score
	/*	scoreArray = scoreArray.add( // just add an empty event
	().add(\time -> 0)
	.add(\amp -> 0)
	.add(\player -> 1) //1 or 2
	.add(\plank -> 1)
	);*/

	sndpath = thisProcess.nowExecutingPath.dirname ++ "/sounds/";
	samples = (sndpath++"*").pathMatch;
	("sndpath is" + sndpath).postln;
	("available samples are" + samples).postln;

	buffers = Array.fill(8, {[nil,false, false]});// [Buffer, enabledtxakun, enablederrena]
	buffers[0][1] = true; // but enable the first one

	presetspath = thisProcess.nowExecutingPath.dirname ++ "/presets/";
	presets = (presetspath++"*").pathMatch;

	beatbuttons = [Array.fill(5, {nil}), Array.fill(5, {nil})];
	beatsliders = Array.fill(5, {nil});
	sliders = Array.fill(4, {[nil,nil]}); // slider and its autopilot button associated
	slidersauto = Array.fill(4, {nil}); // keep a ref to the ones available for autopilot
	makilasliders = [[nil, nil], [nil, nil]]; // two for each player
	planksMenus = Array.fill(buffers.size, {[nil,nil,nil]});// [tx button, err button, pulldownmenu] txakun and errena separated enabled
	enabledButs = [nil, nil]; // txakun and errena

	drawingSet = [Array.fill(4, {[0, false, 10]}), Array.fill(4, {[0, false, 10]})]; //one buelta with 4 hits each part

	netadd = NetAddr("127.0.0.1", 6666);// this needs to be set to a proper port
	midioutdev = MIDIOut(0); // activate MIDI out device





	// THE BASIC SYNTHDEF

	SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
		Out.ar(outbus,
			amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
		)
	}).add;



	/* return true if any of the items in the array or list is not nil
	*/
	getplankindex = { arg plank;
		var pos=0;
		buffers.do({arg buf, index;
			if (buf[0].bufnum==plank[0].bufnum, {
				pos = index;
			});
		});

		pos+1;
	};

	getnumactiveplanks = {
		var numactiveplanks=0;
		buffers.do({arg arr;
			if( (arr[1]||arr[2]), {numactiveplanks=numactiveplanks+1})
		});
		numactiveplanks;
	};

	istheresomething = {arg alist;
		var values = List[];
		alist.do({arg item;
			if (item!=nil, {values.add(true)});
		});
		if(values.size>0, {true}, {false});
	};

	findIndex = {arg plankmenu, path;
		var returnval=0;
		//plankmenu.postln;
		plankmenu.items.do({arg file, i;
			if (sndpath++file==path,{returnval = i});
		});
		returnval;
	};

	/* this is just to avoid the data being overwriten
	*/
	scheduleDraw = {arg data;
		drawingSet[1] = data;
		window.refresh;
	};

	postOutput = {arg outarray;
		outarray.do({arg item;
			if (~verbose>item[0], {item[1].postln});
		});
		outarray = [];
	};


	/* this schedules the FIRST hit on the bar. subsequent hits go after
	*/
	dohits = {arg step, txakun, localamp, localstep, intermakilaswing, numbeats, localtemposwing, emph;

		var firstdefer=nil, drawingSetB = Array.fill(8, {[0, false, 10]}), flagindex=1, outarray=Array.new;

		// txakun true 1 -> 1 // errena false 0 -> 2
		if (txakun, {flagindex=1},{flagindex=2});

		// avoid when no sound is selected
		if (buffers.deepCollect(1, {|item| item[flagindex]}).find([true]).isNil.not,
			{
				if(~mode, { // reverse in old mode
					if(~lastemphasis,
						{emph = 0},
						{emph = 1+((numbeats-1).rand)}
					);
					},{
						if(~lastemphasis,
							{emph = numbeats-1},
							{emph = (numbeats-1).rand}
						);
				});

				numbeats.do({ arg index; // for each makila one hit
					var hittime, hitfreq, hitamp, hitswing, makilaindex, plank=[nil, false, false];
					if (~amp > 0, { // emphasis on first or on last hit?
						if (index == emph, {
							hitamp = localamp + (localamp/5);
							},{
								hitamp = localamp + rrand(-0.06, 0.06) ;
						});
					});

					{ plank[flagindex] == false }.while( {
						plank = buffers.wchoose(~plankchance.normalizeSum)
					});

					outarray = outarray.add([2, "plank"+plank]);

					hitfreq = (~freqs.choose) + 0.6.rand; // just a small freq swing
					hitswing = localstep + rrand(intermakilaswing.neg, intermakilaswing);
					if( ~mode, { // before the bar
						hittime = localtemposwing - (hitswing * index);
						makilaindex = numbeats-index-1;//reverse
						},{ // aftr the bar
							hittime = localtemposwing + (hitswing * index);
							makilaindex = index;
						}
					);

					if (firstdefer == nil, {firstdefer=hittime}); // schedule drawing later
					scoreArray = scoreArray.add(
						().add(\time -> (Main.elapsedTime - startTime + hittime))
						.add(\amp -> hitamp)
						.add(\player -> flagindex) //1 or 2
						.add(\plank -> getplankindex.value(plank)) //
					);

					{// deferred function
						Synth(\playBuf, [\amp, hitamp, \freq, 1+rrand(-0.008, 0.008), \bufnum, plank[0].bufnum]);
						makilaF.value(makilasliders[txakun.not.asInteger].wrapAt(makilaindex), 0.2);//slider animation
						if (~oscout,{ netadd.sendMsg("/txalaparta", [txakun, hitamp, plank[0].path])});
						if (~midiout, {
							//midioutdev.noteOn(16, 60, 60);
							//midioutdev.noteOff(16, 61, 60);
							//midioutdev.allNotesOff(16);
						});

						outarray = outarray.add([2, ["hit", hittime, hitamp, hitfreq, hitswing]]);

						postOutput.value(outarray);
					}.defer(hittime);
					drawingSetB[index] = [hittime, txakun, hitamp]; // store for drawing on window.refresh

				}); // END NUMBEATS LOOP}

				{
					scheduleDraw.value(drawingSetB);
				}.defer(firstdefer); // finally schedule drawing
		}, {"WARNING: no sound selected for beat".postln; buffers.postln});
	};





	// TXALAPARTA ////////////////////
	playF = Routine({
		var txakun; // classical txakun only is limited to two beats
		var intermakilaswing, localstep, idealtempo, localtemposwing, localamp, zeroflag=false;

		txakun = true; // play starts with txakun
		nextautopilot = 0;

		inf.do({ arg stepcounter; // txakun > errena cycle
			var numbeats, outstr, beats, outarray=Array.new; // reset each loop

			outarray = outarray.add([1, ("is txakun?" + txakun)]);

			beats =	~allowedbeats[txakun.not.asInt];

			// autopilot
			if (( istheresomething.value(slidersauto) &&
				(stepcounter >= nextautopilot)) , {
					var sl;
					{ sl == nil }.while( {sl = slidersauto.choose} ) ;
					{
						sl.valueAction = rrand(sl.controlSpec.minval, sl.controlSpec.maxval);
					}.defer;
					nextautopilot = stepcounter + rrand(~autopilotrange[0], ~autopilotrange[1]); // next buelta to change
					outarray = outarray.add([0, ("autopilot! next at" + nextautopilot)]);
			});

			idealtempo = 60/~tempo; // ideal position
			localtemposwing = (60/~tempo) + ~swing.rand - (~swing/2); //offset

			if (~pulse, // sets the tempo
				{idealtempo.wait},
				{localtemposwing.wait}
			);

			// if none is allowed or if only 0 is allowed or no choices to choose any
			// TO DO. more complex. needs to check if the allowed ones have valid choice
			if ((beats.copyRange(1,beats.size).every(_.isNil) ||
				~beatchance.normalizeSum.every(_.isNaN)), {
				"WARNING: no beats allowed or no choice to select any".postln;
				},{
					// beats
					if ( (txakun && ~enabled[0]) || (txakun.not && ~enabled[1]), // enabled?
						{
							if ((~zerolimit && zeroflag),{ // no two consecutive 0
								beats=beats[1..beats.size];
							});

							{ numbeats == nil }.while({
								numbeats = beats.wchoose(~beatchance.normalizeSum)
							});

							if (numbeats==0, {zeroflag=true}, {zeroflag=false});

							// global to all hits in this step
							if (~pulse, // calculates de ~gap
								{localstep = (idealtempo*~gap*2)/numbeats},
								{localstep = (localtemposwing*~gap*2)/numbeats}
							);

							intermakilaswing = rrand(~gapswing/numbeats.neg, ~gapswing/numbeats); //reduces  swing proportionally to hits to play
							if (~amp > 0, {localamp = ~amp + 0.3.rand-0.15}, {localamp = 0}); //local amp swing
							dohits.value(stepcounter, txakun, localamp, localstep, intermakilaswing, numbeats, localtemposwing);

							outstr = stepcounter.asString++":"+if(txakun, {"txakun"},{"errena"})+numbeats;
							outarray = outarray.add([1, ["beat", stepcounter, txakun, numbeats]]);

							{
								output.string = outstr;
								postOutput.value(outarray);
							}.defer(localtemposwing);
					}); //end if beats

			});

			txakun = txakun.not;
		}) // end inf loop
	});






	// GUI /////////////////////////////////////////

	// txalascore animation

	txalascoreF = Routine({
		inf.do({ //arg count;
			if (playBut.value.asBoolean, {
				{tscore.update(scoreArray, Main.elapsedTime - startTime)}.defer;
			});
			0.05.wait;
		});
	});


	// MAKILA PLAYING ANIMATION //
	makilaF = {arg sl, time;
		var steps, stepvalue, gap=0.05, loopF;
		steps = (time/gap).asInt;
		stepvalue = 1/steps;

		sl.value = 1;

		loopF = Routine({
			sl.knobColor = Color.red;
			(steps*2).do({ arg i;
				sl.value = sl.value - stepvalue;
				if (i == (steps-1), { stepvalue = stepvalue.neg });
				gap.wait;
			});
			sl.knobColor = Color.black;
			//window.refresh;
		});

		AppClock.play(loopF);
	};


	// GUI ELEMENTS ////

	// WINDOW
	doWindow = {arg width, height, caption;
		var rot=0;

		window = Window(caption, Rect(100, 100, width, height));
		//window.alwaysOnTop = true;
		window.onClose = {
			AppClock.clear;
			SystemClock.clear;
		};
		window.front;

		window.drawFunc = { // drawing the visualization of circles
			var dur, dpt, rot; // duration of the circle and degrees per time unit
			dur = 120/~tempo; // duration of the cycle in secs
			dpt = 360/dur; // how many degrees each ms
			rot = (((drawingSet[1][0][0]*dpt)))*(pi/180); // apply the rotation of the current beat

			Pen.translate(565, 520);
			Pen.color = Color.black;
			Pen.addArc(0@0, 80, 0, 360);

			Pen.perform(\stroke); // static if maintaining pulse

			//drawHitSet.value(drawingSet[0], dur, dpt);

			if (~pulse.not, { Pen.rotate( rot ) });

			Pen.line(0@90.neg, 0@90); //vertical line
			Pen.perform(\stroke); // static if maintaining pulse

			drawHitSet.value(drawingSet[1], dur, dpt);

			drawingSet = [ drawingSet[1], Array.fill(4, {[0, false, 10]}) ];

		};

		/*
		clock = UserView(window, Rect(380-45, 375-45, 90, 90));
		//clock.background = Color.black;
		clock.animate = true;
		clock.drawFunc = {
		Pen.color = Color.red;
		rot = rot + 2pi/(120/~tempo);
		Pen.rotate( rot );
		Pen.line(0@0, 0@45.neg); //rotating line
		};*/

		/*window.view.keyDownAction = { arg view, char, modifiers, unicode, keycode;
		//[char, keycode].postln;
		if (keycode=65, {playBut.});
		}*/
	};

	drawHitSet = { arg set, dur, dpt;
		set.do({arg data; // --> [msecs, txakunflag, ]
			var offset;

			if (data[0]>0, { // only the ones with a valid value
				if (data[1], //txakun
					{
						offset = 270;
						Pen.color = Color.red; //.alpha_(0.8).set;
					},
					{
						offset = 90;
						Pen.color = Color.blue;
					}
				); // txakun up, errena down

				Pen.use{
					Pen.rotate( (((data[0]*dpt)-offset)*(pi/180)) );
					Pen.addArc((80)@(0), data[2]*12, 0, 360); // circles representing beats
					Pen.perform(\fill);
				};
			});
		});
	};



	// TIME CONTROLS
	doTimeControls = { arg xloc = 10, yloc=5, width=360, gap=24;
		var buttonxloc = xloc + width + 20;

		// tempo //
		sliders[0][0] = EZSlider( window,         // parent
			Rect(xloc,yloc,width,20),    // bounds
			"tempo",  // label
			ControlSpec(30, 550, \lin, 1, ~tempo, "BPMs"),     // controlSpec
			{ arg ez;
				~tempo = ez.value;
			},
			initVal: ~tempo,
			labelWidth: 80;
		);

		Button(window, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			// "this should open a parameter editing window".postln;
			ParamWin.new("~tempo", ControlSpec(30, 550));
		});

		sliders[0][1] = Button(window, Rect(buttonxloc,yloc,60,20))
		.states_([
			["autopilot", Color.white, Color.black],
			["autopilot", Color.black, Color.green],
		])
		.action_({ arg butt;
			if (butt.value.asBoolean,
				{slidersauto[0]=sliders[0][0]}, {slidersauto[0]=nil});
		})
		.valueAction_(0);

		// tempo swing //
		yloc = yloc+gap;
		sliders[1][0] = EZSlider( window,         // parent
			Rect(xloc,yloc,width,20),    // bounds
			"tempo swing",  // label
			ControlSpec(0.001, 1, \lin, 0.001, ~swing, "ms"),     // controlSpec
			{ arg ez;
				~swing = ez.value;
			},
			initVal: ~swing,
			labelWidth: 80;
		);

		Button(window, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~swing", ControlSpec(0.001, 1));
		});

		sliders[1][1] = Button(window, Rect(buttonxloc,yloc,60,20))
		.states_([
			["autopilot", Color.white, Color.black],
			["autopilot", Color.black, Color.green],
		])
		.action_({ arg butt;
			if (butt.value.asBoolean,
				{slidersauto[1]=sliders[1][0]}, {slidersauto[1]=nil});
		})
		.valueAction_(0);


		// gap //
		yloc = yloc+gap;
		sliders[2][0] = EZSlider( window,         // parent
			Rect(xloc,yloc,width,20),    // bounds
			"gap",  // label
			ControlSpec(0.001, 1, \lin, 0.001, ~gap, "ms"),     // controlSpec
			{ arg ez;
				~gap = ez.value;
			},
			initVal: ~gap,
			labelWidth: 80;
		);

		Button(window, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~gap", ControlSpec(0.001, 1));
		});

		sliders[2][1] = Button(window, Rect(buttonxloc,yloc,60,20))
		.states_([
			["autopilot", Color.white, Color.black],
			["autopilot", Color.black, Color.green],
		])
		.action_({ arg butt;
			if (butt.value.asBoolean,
				{slidersauto[2]=sliders[2][0]}, {slidersauto[2]=nil});
		})
		.valueAction_(0);

		// gap swing //
		yloc = yloc+gap;
		sliders[3][0] = EZSlider( window,         // parent
			Rect(xloc,yloc,width,20),    // bounds
			"gap swing",  // label
			ControlSpec(0.001, 1, \lin, 0.001, ~gapswing, "ms"),     // controlSpec
			{ arg ez;
				~gapswing = ez.value;
			},
			initVal: ~gapswing,
			labelWidth: 80;
		);

		Button(window, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~gapwing", ControlSpec(0.001, 1));
		});

		sliders[3][1] = Button(window, Rect(buttonxloc,yloc,60,20))
		.states_([
			["autopilot", Color.white, Color.black],
			["autopilot", Color.black, Color.green],
		])
		.action_({ arg butt;
			if (butt.value.asBoolean,
				{slidersauto[3]=sliders[3][0]}, {slidersauto[3]=nil});
		})
		.valueAction_(0);


		// amplitude does not go with autopilot and therefore is stored in its own var
		yloc = yloc+gap;
		ampBut = EZSlider( window,         // parent
			Rect(xloc,yloc,width,20),   // bounds
			"amp",  // label
			ControlSpec(0, 1, \lin, 0.01, ~amp, "ms"), //\amp,     // controlSpec
			{ arg ez;
				~amp = ez.value;
			},
			initVal: ~amp,
			labelWidth: 80;
		);
		Button(window, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~amp", ControlSpec(0, 1));
		});
	};





	// BOTONES
	doButtons = { arg xloc=10, yloc = 110;
		var beatsxloc = 220;

		// AUTOPILOT
		/*Button(window, Rect(xloc+200,yloc,100,25))
		.states_([
		["Autopilot", Color.white, Color.black],
		["Autopilot", Color.black, Color.red],
		])
		.action_({ arg butt;
		~autopilot = butt.value.asBoolean;
		nextautopilot = 0; // force go next round
		if (~verbose>0 && ~autopilot, {("next autopilot scheduled for step" + nextautopilot).postln});
		});*/
		//.valueAction_(0);


		// PULSE
		pulseBut = Button(window, Rect(xloc,yloc,100,25))
		.states_([
			["maintain pulse", Color.white, Color.black],
			["maintain pulse", Color.black, Color.green],
		])
		.action_({ arg butt;
			~pulse = butt.value.asBoolean;
		});

		// EMPHASIS
		emphasisBut = Button(window, Rect(xloc+100,yloc,100,25))
		.states_([
			["last emphasis", Color.white, Color.black],
			["last emphasis", Color.black, Color.green],
		])
		.action_({ arg butt;
			~lastemphasis = butt.value.asBoolean;
		})
		.valueAction_(1);


		// ZAHARRA MODE
		Button(window, Rect(xloc+100,yloc+25,100,25))
		.states_([
			["go zaharra", Color.white, Color.black],
		])
		.action_({ arg butt;
			beatbuttons.do({arg butset;
				butset.do({arg but, ind;
					if ( but != nil, {
						if ( ind < 3, {but.valueAction = 1}, {but.valueAction = 0});
						oldpulseBut.valueAction = 1;
						emphasisBut.valueAction = 1;
						pulseBut.valueAction = 0
					});
				});

			});
		})
		.valueAction_(1);



		// BEATS
		StaticText(window, Rect(beatsxloc, yloc-16, 200, 20)).string = "Hits";
		StaticText(window, Rect(beatsxloc+40, yloc-16, 200, 20)).string = "% chance";

		~allowedbeats[0].size.do({arg subindex;
			2.do({arg index; // two players
				var thecolor;
				if (index%2==0, {thecolor=Color.red}, {thecolor=Color.blue});

				beatbuttons[index][subindex] = Button(window, Rect(beatsxloc+(20*index),yloc+(25*subindex),20,25))
				.states_([
					[subindex.asString, Color.white, Color.black],
					[subindex.asString, Color.black, thecolor],
				])
				.action_({ arg butt;
					if (butt.value.asBoolean,
						{~allowedbeats[index][subindex] = subindex},
						{~allowedbeats[index][subindex] = nil});
				});
				beatbuttons[index][subindex].valueAction = 0;
			});

			beatsliders[subindex] = Slider(window,
				Rect(beatsxloc+40,yloc+(25*subindex),75,25))
			.action_({arg sl;
				~beatchance[subindex] = sl.value;
			}).orientation = \horizontal;
			beatsliders[subindex].valueAction = ~beatchance[subindex];
		});

		beatbuttons[0][2].valueAction = 1; // activate by default
		beatbuttons[1][2].valueAction = 1;


		// Allow repeat 0 more than once
		zerolimitBut = Button(window, Rect(beatsxloc,yloc+140,100,25))
		.states_([
			["limit 0", Color.white, Color.black],
			["limit 0", Color.black, Color.green],
		])
		.action_({ arg butt;
			~zerolimit = butt.value.asBoolean;
		})
		.valueAction_(1);


		// txakascore timeline
		Button(window, Rect(beatsxloc,yloc+170,100,25))
		.states_([
			["show timeline", Color.white, Color.black],
		])
		.action_({ arg butt;
			doTxalaScore.value();
		});


		// MODE
		oldpulseBut = Button(window, Rect(xloc,yloc+25,100,25))
		.states_([
			["old pulse", Color.white, Color.black],
			["old pulse", Color.black, Color.green],
		])
		.action_({ arg butt;
			~mode = butt.value.asBoolean;
		}).valueAction = 1;


		// PLAY
		playBut = Button(window, Rect(xloc,yloc+200,200,50))
		.states_([
			["play/stop", Color.white, Color.black],
			["play/stop", Color.black, Color.green],
		])
		.action_({ arg butt;
			if ( butt.value.asBoolean, {
				SystemClock.play(playF);
				startTime = Main.elapsedTime;
				},{
					SystemClock.clear;
					startTime = 0;
					scoreArray.postln;
			});
		});
		//.defaultKeyDownAction_(" ");
		//playBut.defaultKeyDownAction = "";

		// RANDOM MODE
		Button(window, Rect(beatsxloc,yloc+200,100,25))
		.states_([
			["randomise", Color.white, Color.black],
		])
		.action_({ arg butt;
			"TO DO: randomise all controls".postln;
			sliders.do({arg slarray;
				//slarray[0].valueAction = 0;// to do random
			});
			beatsliders.do({arg slarray;
				//slarray.valueAction = 0;
			});
			beatbuttons.do({arg slarray;
				//slarray.valueAction = 0;
			});
		})
		.valueAction_(1);


		// SERVER
		Button(window, Rect(xloc,yloc+78,100,25))
		.states_([
			["server window", Color.white, Color.grey],
		])
		.action_({ arg butt;
			s.makeGui;
		});

		// VERBOSE
		Button(window, Rect(xloc+100,yloc+78,20,25))
		.states_([
			["V", Color.white, Color.grey],
			["V", Color.white, Color.blue],
			["V", Color.white, Color.green],
			["V", Color.white, Color.red]
		])
		.action_({ arg butt;
			~verbose = butt.value;
		})
		.valueAction_(~verbose);

		// OSC OUT
		Button(window, Rect(xloc+120,yloc+78,80,25))
		.states_([
			["send OSC", Color.white, Color.grey],
			["send OSC", Color.black, Color.blue],
		])
		.action_({ arg butt;
			~oscout = butt.value.asBoolean;
		})
		.valueAction_(0);

		// MIDI OUT
		Button(window, Rect(xloc+120,yloc+105,80,25))
		.states_([
			["send MIDI", Color.white, Color.grey],
			["send MIDI", Color.black, Color.blue],
		])
		.action_({ arg butt;
			"TO DO, send MIDI events".postln;
			~midiout = butt.value.asBoolean;
		})
		.valueAction_(0);
	};





	// PLANKS - OHOLAK //////////////////////////////////
	doPlanks = { arg xloc=10, yloc = 260;
		var menuxloc = xloc + 44, playxloc = menuxloc+250+2;
		var yp=0;

		StaticText(window, Rect(xloc, yloc-18, 200, 20)).string = "TX";
		StaticText(window, Rect(xloc+22, yloc-18, 200, 20)).string = "ER";
		StaticText(window, Rect(menuxloc, yloc-18, 200, 20)).string = "Oholak/Planks";
		StaticText(window, Rect(menuxloc+280, yloc-16, 200, 20)).string = "% chance";

		////////////////
		buffers.size.do({ arg index;

			// txakun row buttons
			planksMenus[index][0] = Button(window, Rect(xloc,yloc+yp,20,20))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.black, Color.red],
			])
			.action_({ arg butt;
				buffers[index][1] = butt.value.asBoolean;
				updateTxalaScoreNumPlanks.value();
			});

			// errena row buttons
			planksMenus[index][1] = Button(window, Rect(xloc+22,yloc+yp,20,20))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.black, Color.blue],
			])
			.action_({ arg butt;
				buffers[index][2] = butt.value.asBoolean;// THIS SHOULD GO TO ANOTHER?
				updateTxalaScoreNumPlanks.value();
			});

			if (index==0, {
				planksMenus[index][0].valueAction = 1;
				planksMenus[index][1].valueAction = 1;
			});// ONLY activate first ones

			// menus
			planksMenus[index][2] = PopUpMenu(window,Rect(menuxloc,yloc+yp,250,20))
			.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
			.action_({ arg menu;
				buffers[index][0] = Buffer.read(s, sndpath ++ menu.item);
				("loading" + menu.item + "with bufnum" + buffers[index][0].bufnum).postln;
			})
			.valueAction_(index);

			// play buttons row
			Button(window, Rect(playxloc,yloc+yp,20,20))
			.states_([
				[">", Color.white, Color.black]
			])
			.action_({ arg butt;
				Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum,
					buffers[index][0].bufnum]
				)
			});

			Slider(window,Rect(menuxloc+275,yloc+yp,75,20))
			.action_({arg sl;
				~plankchance[index] = sl.value;
			})
			.orientation_(\horizontal)
			.valueAction_(~plankchance.[index]);

			yp = yp + 20; // px
		});

	};



	// MAKILAS
	doMakilas = { arg xloc=300, yloc=190, gap=35;
		var ind = 0, thegap = 0;

		makilasliders.do({arg list;
			list.do({arg item, i;
				list[i] = Slider(window, Rect(xloc+thegap+(61*ind), yloc, 60, 350));
				list[i].orientation = \vertical;
				list[i].thumbSize = 240;
				list[i].value = 1;
				ind = ind + 1;
			});
			thegap = gap;
		});

		// TXAKUN
		enabledButs[0] = Button(window, Rect(xloc,yloc+350,120,50))
		.states_([
			["txakun", Color.white, Color.black],
			["txakun", Color.black, Color.red],
		])
		.action_({ arg butt;
			~enabled[0] = butt.value.asBoolean;
		})
		.valueAction_(1);

		// ERRENA
		enabledButs[1] = Button(window, Rect(xloc+130,yloc+350,120,50))
		.states_([
			["errena", Color.white, Color.black],
			["errena", Color.black, Color.blue],
		])
		.action_({ arg butt;
			~enabled[1] = butt.value.asBoolean;
		})
		.valueAction_(1);

		output = StaticText(window, Rect(xloc, yloc+400, 200, 20));

	};


	updateTxalaScoreNumPlanks = {arg numactiveplanks;
		if (timelinewin.isNil.not, {
			var numactiveplanks = getnumactiveplanks.value();

/*			buffers.do({arg arr;
				if( (arr[1]||arr[2]), {numactiveplanks=numactiveplanks+1})
			});*/
			tscore = nil;
			tscore = TxalaScore.new(timelinewin,
				Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25),
				numactiveplanks);
		});
	};


	// creates the timeline window
	doTxalaScore = { arg xloc=0, yloc=600, width=1020, height=350, timeframe=4;
		var view, xstep=0, drawspeed=1, numactiveplanks=0;
		if (timelinewin.isNil, {
			timelinewin = Window("Timeline", Rect(xloc, yloc, width, height));

			numactiveplanks = getnumactiveplanks.value();

/*			buffers.do({arg arr;
				if( (arr[1]||arr[2]), {numactiveplanks=numactiveplanks+1})
			});*/

			tscore = TxalaScore.new(timelinewin,
				Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25),
				numactiveplanks);

			tscore.timeframe = timeframe;
			//tscore.recordScore = true;

			EZSlider( timelinewin,         // parent
				Rect(-40,timelinewin.bounds.height-22,200,20),    // bounds
				"zoom",  // label
				ControlSpec(20, 1, \lin, 0.001, 10, "ms"),     // controlSpec
				{ arg ez;
					tscore.timeframe = ez.value;
				},
				initVal: timeframe,
				labelWidth: 80;
			);

			Button(timelinewin, Rect(200,timelinewin.bounds.height-22,75,20))
			.states_([
				["save score", Color.white, Color.black]
			])
			.action_({ arg butt;
				"this should save the score to a MIDI file".postln;
			});

			AppClock.play(txalascoreF);

			timelinewin.onClose = {timelinewin=nil}; // only one instance please
			timelinewin.front;
			//timelinewin.alwaysOnTop = true;
		});
	};


	doPresets = { arg xloc, yloc;
		var popupmenu, newpreset;

		StaticText(window, Rect(xloc, yloc-18, 200, 20)).string = "Presets";

		PopUpMenu(window,Rect(xloc,yloc,200,20))
		.items_(presets.asArray.collect({arg item; PathName.new(item).fileName}))
		.mouseDownAction_({arg menu;
			presets = (presetspath++"*").pathMatch;
			presets.insert(0, "---");
			menu.items = presets.asArray.collect({arg item;
				PathName.new(item).fileName});
		})
		.action_({ arg menu;
			var data;
			("loading..." + presetspath ++ menu.item).postln;
			data = Object.readArchive(presetspath ++ menu.item);
			data.asCompileString.postln;

			~tempo = data[\tempo];
			sliders[0][0].value = ~tempo;//slider
			sliders[0][1].value = data[\slidersauto][0].asInt;//button
			if (data[\slidersauto][0]==true,
				{slidersauto[0]=sliders[0][0]}, {slidersauto[0]=nil});

			~swing = data[\swing];
			sliders[1][0].value = ~swing;//slider
			sliders[1][1].value = data[\slidersauto][1].asInt;//button
			if (data[\slidersauto][1]==true,
				{slidersauto[1]=sliders[1][0]}, {slidersauto[1]=nil});

			~gap = data[\gap];
			sliders[2][0].value = ~gap;
			sliders[2][1].value = data[\slidersauto][2].asInt;
			if (data[\slidersauto][2]==true,
				{slidersauto[2]=sliders[2][0]}, {slidersauto[2]=nil});

			~gapswing = data[\gapswing];
			sliders[3][0].value = ~gapswing;
			sliders[3][1].value = data[\slidersauto][3].asInt;
			if (data[\slidersauto][3]==true,
				{slidersauto[3]=sliders[3][0]}, {slidersauto[3]=nil});

			~amp = data[\amp];
			ampBut.value = ~amp;

			~allowedbeats = data[\allowedbeats];
			if(~allowedbeats.size>2, // backwards compatible with old presets
				{~allowedbeats=[~allowedbeats, [nil,nil,nil,nil,nil]]
			});

			try { //bckwads compatible again
				beatbuttons.do({arg playerbuttons, index;
					playerbuttons.do({arg but, subindex;
						but.value = ~allowedbeats[index][subindex].asBoolean.asInt; // 0 or 1
/*						if (~allowedbeats[index][subindex]!=nil,
							{but.value = 1},
							{but.value = 0}
						);*/
					});
				});
			} {|error|
				["setting beat buttons error", error, ~allowedbeats].postln;
				beatbuttons[1][2].value = 1; // emergency activate this one
			};

			~pulse = data[\pulse];
			pulseBut.value = ~pulse;

			~lastemphasis = data[\emphasis];
			try {
				emphasisBut.value = ~lastemphasis.asInt;
			} {|error|
				~lastemphasis = data[\emphasis][1]; //bkwds compatibility
			};

			~enabled = data[\enabled];
			enabledButs[0].value = ~enabled[0];
			enabledButs[1].value = ~enabled[1];
			// txakun-errena buttons
			~autopilotrange = data[\autopilotrange]; // no widget!

			try {
				~plankchance = data[\plankchance];
				// to do update widgets!!!
			} {|error|
				"not plankchance in preset".postln;
			};

			try {
				~beatchance = data[\beatchance];
				beatsliders.do({arg beatsl, index;
					beatsl.valueAction = ~beatchance[index];
				});
			} {|error|
				"not beatchance in preset".postln;
			};


			planksMenus.do({arg plank, i;
				try {
					plank[0].valueAction = data[\buffers][i][1].asInt;
				} {|error|
					plank[0].valueAction = 0;
					["catch plank0 error", error, i].postln;
				};

				try {
					plank[1].valueAction = data[\buffers][i][2].asInt;// set er button
				} {|error|
					plank[1].valueAction = 0;
					["catch plank1 error", error, i].postln;
				};

				try {
					plank[2].valueAction = findIndex.value(plank[2], data[\buffers][i][0]);
				} {|error|
					plank[2].valueAction = 0;
					["catch plank2 error", error, i].postln;
				};

			});

		});
		//.valueAction_(0);

		newpreset = TextField(window, Rect(xloc, yloc+22, 125, 25));

		Button(window, Rect(xloc+130,yloc+22,70,25))
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
			data.put(\tempo, ~tempo);
			data.put(\swing, ~swing);
			data.put(\gap, ~gap);
			data.put(\gapswing, ~gapswing);
			data.put(\amp, ~amp);
			data.put(\allowedbeats, ~allowedbeats);
			data.put(\pulse, ~pulse);
			data.put(\emphasis, ~lastemphasis);
			//data.put(\classictxakun, ~classictxakun);
			data.put(\enabled, ~enabled);
			data.put(\autopilotrange, ~autopilotrange);
			data.put(\beatchance, ~beatchance);
			data.put(\plankchance, ~plankchance);
			data.put(\slidersauto, [
				slidersauto[0]!=nil, // store true or false
				slidersauto[1]!=nil,
				slidersauto[2]!=nil,
				slidersauto[3]!=nil,
			]);
			data.put(\buffers, [ //path to file, tx flag, err flag
				[ buffers[0][0].path, buffers[0][1], buffers[0][2] ],
				[ buffers[1][0].path, buffers[1][1], buffers[1][2] ],
				[ buffers[2][0].path, buffers[2][1], buffers[2][2] ],
				[ buffers[3][0].path, buffers[3][1], buffers[3][2] ],
				[ buffers[4][0].path, buffers[4][1], buffers[4][2] ],
				[ buffers[5][0].path, buffers[5][1], buffers[5][2] ],
				[ buffers[6][0].path, buffers[6][1], buffers[6][2] ],
				[ buffers[7][0].path, buffers[7][1], buffers[7][2] ],
			]);

			data.writeArchive(presetspath++filename);

			newpreset.string = ""; //clean field
		});

	};


	//s.waitForBoot({
	// Now position all different groups of GUI elements
	doWindow.value(700, 620, "Txalaparta. www.ixi-audio.net");
	doTimeControls.value(2, 5);
	doMakilas.value(445, 5, 8);
	doPlanks.value(10, 150);
	doButtons.value(10, 350);
	doPresets.value(10, 490);


	if (~verbose>0, {currentEnvironment.postln});
	if (~verbose>0, {buffers});
	//});

}); // this to be able to run from command line sclang txalaparta.sc


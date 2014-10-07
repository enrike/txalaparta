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

	var playF, makilaF, dohits, dohitsold, presetspath, drawingSet, drawingSetB, netadd;

	// GUI vars
	var window, clock, output, slidersauto, makilasliders, nextautopilot, sndpath, samples, buffers, istheresomething, findIndex, presets;
	// GUI widgets
	var sliders, beatbuttons, beatsliders, classicBut, emphasisBut, zerolimitBut, pulseBut, planksMenus, ampBut, playBut, enabledButs;
	// GUI functions vars
	var doWindow, doMakilas, doTimeControls, doButtons, doPlanks, doPresets, scheduleDraw;

	"STARTING TXALAPARTA !!!!!!!".postln;

	// GLOBAL vars
	~tempo = 70; // tempo. txakun / errena
	~swing = 0.1;
	~gapswing = 0.1;
	~gap = 0.22; // between hits. in txalaparta berria all gaps are more equal
	~amp = 0.5;
	~classictxakun = true; // in txalaparta zaharra the txakun always 2 hits
	~pulse = false; // should we keep stedy pulse in the tempo or not?
	~freqs = [230, 231]; //
	~emphasis = [true,false,false,false]; // which one is stronger. actualy just using first or last
	~zerolimit = true; //allow 0 more than once or not?
	~enabled = [true, true]; // switch on/off txakun and errena
	//~allowedbeats = [0, 1, 2, nil, nil]; // 0,1,2,3,4
	~allowedbeats = [[0, 1, 2, nil, nil], [0, 1, 2, nil, nil]];
	~beatchance = [0.15, 0.25, 0.35, 0.15, 0.1];
	~autopilotrange = [5, 10]; // for instance
	~mode = true; // old style hit position calulation?
	~oscout = false;

	// utility
	~verbose = 1;


	sndpath = thisProcess.nowExecutingPath.dirname ++ "/sounds/";
	samples = (sndpath++"*").pathMatch;
	("sndpath is" + sndpath).postln;
	("available samples are" + samples).postln;

	//buffers = [[nil, true, true], [nil, false, false], [nil, false, false], [nil, false, false],[nil, false, false], [nil, false, false], [nil, false, false], [nil, false, false]]; // [Buffer, enabledtxakun, enablederrena]
	buffers = Array.fill(8, {[nil,false, false]});
	buffers[0][1] = true; //enable the first one
	// FIRST IS ENABLED!

	presetspath = thisProcess.nowExecutingPath.dirname ++ "/presets/";
	presets = (presetspath++"*").pathMatch;

	beatbuttons = [Array.fill(5, {nil}), Array.fill(5, {nil})];
	beatsliders = Array.fill(5, {nil});
	sliders = Array.fill(4, {[nil,nil]}); // slider and its autopilot button associated
	slidersauto = Array.fill(4, {nil}); // keep a ref to the ones available for autopilot
	makilasliders = [[nil, nil], [nil, nil]]; // two for each player
	planksMenus = Array.fill(buffers.size, {[nil,nil,nil]});// [tx button, err button, pulldownmenu] txakun and errena separated enabled
	enabledButs = [nil, nil]; // txakun and errena

	drawingSet = Array.fill(4, {[0,false]}); // delay time from pulse and txakun or not?


	netadd = NetAddr("127.0.0.1", 6666);





	// THE BASIC SYNTHDEF

	SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
		Out.ar(outbus,
			amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
		)
	}).add;



	/* return true if any of the items in the array or list is not nil
	*/
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
		drawingSet = data;
		window.refresh;
	};


	/* this schedules the FIRST hit on the bar. subsequent hits go after
	*/
	dohits = {arg txakun, localamp, localstep, intermakilaswing, numbeats, localtemposwing;

		var firstdefer=nil, drawingSetB = Array.fill(8, {[0, false]}), flagindex=1; // buffer

		// txakun true 1 -> 1 // errena false 0 -> 2
		if (txakun, {flagindex=1},{flagindex=2});

		// avoid when no sound is selected
		if (buffers.deepCollect(1, {|item| item[flagindex]}).find([true]).isNil.not,
		{
		numbeats.do({ arg index; // for each makila one hit
			var hittime, hitfreq, hitswing, hitamp, makilaindex, plank=[nil, false, false];
			if (~amp > 0, { // emphasis on first or on last hit?
				if ((numbeats == (index+1) && ~emphasis[1]) || (index == 0 && ~emphasis[0]),
					{hitamp = localamp + 0.30},
					{hitamp = localamp}
				);
			});

			{ plank[flagindex] == false }.while( { plank = buffers.choose });// avoid false,

			//{ plank[txakun.asInt] == false }.while( { plank = buffers.choose })
			//{ plank[1] == false }.while( { plank = buffers.choose }); // avoid nil
			if (~verbose>2, {("plank"+plank).postln});

			hitfreq = (~freqs.choose) + 0.6.rand; // just a small freq swing
			hitswing = localstep + rrand(intermakilaswing.neg, intermakilaswing);
			if( ~mode, // before the bar
				{
					hittime = localtemposwing - (hitswing * index);
					makilaindex = numbeats-index-1;//reverse
				},
				{ // aftr the bar
					hittime = localtemposwing + (hitswing * index);
					makilaindex = index;
				}
			);

			if (firstdefer == nil, {firstdefer=hittime}); // schedule drawing later

			{// deferred function
				Synth(\playBuf, [\amp, hitamp, \freq, 1+rrand(-0.008, 0.008), \bufnum, plank[0].bufnum]);
				makilaF.value(makilasliders[txakun.not.asInteger].wrapAt(makilaindex), 0.2);//slider animation
				if (~oscout,{ netadd.sendMsg("/txalaparta", [txakun, hitamp, plank[0].path])});
				if (~verbose>2, {["hit", hittime, hitamp, hitfreq, hitswing].postln});
			}.defer(hittime);

			drawingSetB[index] = [hittime, txakun]; // store for drawing on window.refresh

			// animation
			//{makilaF.value(makilasliders[txakun.not.asInteger].wrapAt(makilaindex), 0.2)}.defer( hittime-0.2);

		}); // END NUMBEATS LOOP}



		{scheduleDraw.value(drawingSetB)}.defer(firstdefer); // finally schedule drawing
		}, {"WARNING: no sound selected for this beat".postln; buffers.postln});
	};








	// TXALAPARTA ////////////////////
	playF = Routine({
		var txakun; // classical txakun only is limited to two beats
		var intermakilaswing, localstep, idealtempo, localtemposwing, localamp;

		txakun = true; // play starts with txakun
		nextautopilot = 0;

		inf.do({ arg stepcounter; // txakun > errena cycle
			var numbeats, outstr, beats; // numbeats needs to be here to be nill each time
			beats =	~allowedbeats[txakun.not.asInt];
			//["beats",beats, txakun.not.asInt, ~allowedbeats].postln;

			if (~verbose>1, {("is txakun?" + txakun).postln});

			// autopilot
			if (( istheresomething.value(slidersauto) &&
				(stepcounter >= nextautopilot)) , {
				var sl;
				//slidersauto.postln;
				{ sl == nil }.while( {sl = slidersauto.choose} ) ;
				{sl.valueAction = rrand(sl.controlSpec.minval, sl.controlSpec.maxval)}.defer;
				nextautopilot = stepcounter + rrand(~autopilotrange[0], ~autopilotrange[1]); // next buelta to change
				if (~verbose>0, {("autopilot! next at" + nextautopilot).postln});
			});

			idealtempo = 60/~tempo; // ideal position
			localtemposwing = (60/~tempo) + ~swing.rand - (~swing/2); //offset

			if (~pulse,
				{idealtempo.wait}, // sets the tempo
				{localtemposwing.wait} // sets the tempo
			);

			// if none is allowed or if only 0 is allowed
			if (beats.copyRange(1,beats.size).every(_.isNil), {
				"WARNING: no beats allowed".postln;
				1.wait;
			},{
				// beats
				if ( (txakun && ~enabled[0]) || (txakun.not && ~enabled[1]), // enabled?
				{
					//if (~zerolimit,{},{});
					if (~classictxakun && txakun, // choose num of beats for this step
						{ numbeats = 2 },
						{
							{ numbeats == nil }.while({
										numbeats = beats.wchoose(~beatchance)
									})
						} // avoid nil
					);

					//if (numbeats==0, {zeroflag=true}, {zeroflag=false});

					// global to all hits in this step
					localstep = ~gap/numbeats; // reduces step proportionally to hits to play
					intermakilaswing = rrand(~gapswing/numbeats.neg, ~gapswing/numbeats); //reduces  swing proportionally to hits to play
					if (~amp > 0, {localamp = ~amp + 0.3.rand-0.15}, {localamp = 0}); //local amp swing
					dohits.value(txakun, localamp, localstep, intermakilaswing, numbeats, localtemposwing);

					outstr = stepcounter.asString++":"+if(txakun, {"txakun"},{"errena"})+numbeats;
					{output.string = outstr}.defer(localtemposwing);


					if (~verbose>0, {["beat", stepcounter, txakun, numbeats].postln});
					if (~verbose>0 && txakun.not, {"-- buelta --".postln}); // every other is a buelta
				}); //end if beats

			});

			txakun = txakun.not;

		}) // end inf loop
	});






	// GUI /////////////////////////////////////////


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
		window.alwaysOnTop = true;
		window.onClose = {AppClock.clear;SystemClock.clear};
		window.front;

		window.drawFunc = { // drawing the visualization of circles
			var dur, dpt; // duration of the circle and degrees per time unit
			dur = 120/~tempo; // duration of the cycle in secs
			dpt = 360/dur; // how many degrees each ms

			Pen.translate(565, 520);
			Pen.color = Color.black;

			Pen.addArc(0@0, 80, 0, 360);

			// rot = rot + (dur/2pi);
			// Pen.rotate( d );
			// Pen.line(0@0, 0@45.neg); //rotating line
			// Pen.rotate( rot.neg );
			// Pen.stroke;

			if (~pulse.not, {
				Pen.rotate( (((drawingSet[0][0]*dpt)))*(pi/180) );
			});

			Pen.line(0@90.neg, 0@90); //vertical line
			Pen.perform(\stroke); // static if maintaining pulse

			drawingSet.do({arg data; // --> [msecs, txakunflag]
				var offset;
				if (data[0]>0, { // only the ones with a valid value
					//["drawing data was", data].postln;
					if (data[1],
						{
							offset = 270;
							Pen.color = Color.red;
						},
						{
							offset = 90;
							Pen.color = Color.blue;
						}
					); // txakun up, errena down

					Pen.use{
						Pen.rotate( (((data[0]*dpt)-offset)*(pi/180)) );
						Pen.addArc((80)@(0), 10, 0, 360); // circles representing beats
						//Pen.stroke;
						Pen.perform(\fill);
					};
				});
			});


			Pen.perform(\stroke);

			drawingSet = Array.fill(4, {[0, false]});//clear

		};
		window.refresh;
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



	// TIME CONTROLS
	doTimeControls = { arg xloc = 10, yloc=5, width=360, gap=24;
		var buttonxloc = xloc + width + 5;

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
			~emphasis = [butt.value.asBoolean.not, butt.value.asBoolean];
		})
		.valueAction_(1);


		// ZAHARRA MODE
		Button(window, Rect(xloc+100,yloc+25,100,25))
		.states_([
			["go zaharra", Color.white, Color.black],
		])
		.action_({ arg butt;
			~classictxakun = true; //butt.value.asBoolean.not;
			beatbuttons.do({arg butset;
				butset.do({arg but, ind;
				if ( but != nil, {
					if ( ind < 3, {but.valueAction = 1}, {but.valueAction = 0});
					classicBut.valueAction = 1;
					emphasisBut.valueAction = 1;
					pulseBut.valueAction = 0
				});
			});

			});
/*			beatbuttons.do({arg but, ind;
				if ( but != nil, {
					if ( ind < 3, {but.valueAction = 1}, {but.valueAction = 0});
					classicBut.valueAction = 1;
					emphasisBut.valueAction = 1;
					pulseBut.valueAction = 0
				});
			});*/
		})
		.valueAction_(1);



		// BEATS
		StaticText(window, Rect(beatsxloc, yloc-16, 200, 20)).string = "Hits";
		//StaticText(window, Rect(beatsxloc+30, yloc-16, 200, 20)).string = "% weights";

		//~allowedbeats.postln;

		~allowedbeats[0].size.do({arg subindex;
			2.do({arg index;
				var thecolor;
				if (index%2==0, {thecolor=Color.red}, {thecolor=Color.blue});
				//beatbuttons.postln;
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

/*			beatsliders[subindex] = Slider(window,
				Rect(beatsxloc+40,yloc+(25*subindex),75,25))
			.action_({

			}).orientation = \horizontal;*/
		});

		beatbuttons[0][2].valueAction = 1; // activate by default
		beatbuttons[1][2].valueAction = 1;

		// CLASSIC TXAKUN
		classicBut = Button(window, Rect(beatsxloc,yloc+125,100,25))
		.states_([
			["classic txakun", Color.white, Color.black],
			["classic txakun", Color.black, Color.green],
		])
		.action_({ arg butt;
			~classictxakun = butt.value.asBoolean;
		});
		classicBut.valueAction = 1;



		// Allow repeat 0 more than once
		zerolimitBut = Button(window, Rect(beatsxloc,yloc+150,100,25))
		.states_([
			["limit 0", Color.white, Color.black],
			["limit 0", Color.black, Color.green],
		])
		.action_({ arg butt;
			~zerolimit = butt.value.asBoolean;
		})
		.valueAction_(1);

		// MODE
		Button(window, Rect(xloc,yloc+25,100,25))
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
			if ( butt.value.asBoolean, { SystemClock.play(playF)}, {SystemClock.clear});
		});
		//.defaultKeyDownAction_(" ");
		//playBut.defaultKeyDownAction = "";

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


	};





	// PLANKS - OHOLAK //////////////////////////////////
	doPlanks = { arg xloc=10, yloc = 260;
		var menuxloc = xloc + 44, playxloc = menuxloc+250+2;
		var yp=0;

		StaticText(window, Rect(xloc, yloc-18, 200, 20)).string = "TX";
		StaticText(window, Rect(xloc+22, yloc-18, 200, 20)).string = "ER";

		StaticText(window, Rect(menuxloc, yloc-18, 200, 20)).string = "Oholak/Planks";
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
			});

			// errena row buttons
			planksMenus[index][1] = Button(window, Rect(xloc+22,yloc+yp,20,20))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.black, Color.blue],
			])
			.action_({ arg butt;
				buffers[index][2] = butt.value.asBoolean;// THIS SHOULD GO TO ANOTHER ONE
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
				Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, buffers[index][0].bufnum])
			});

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
			~allowedbeats.postln;
			if(~allowedbeats.size>2, // backwards compatible with old presets
				{~allowedbeats=[~allowedbeats, [nil,nil,nil,nil,nil]]
			});
~allowedbeats.postln;
			try { //bckwads compatible again
				beatbuttons.size.do({arg index;
					beatbuttons[index].do({ arg subindex;
						if (~allowedbeats[index][subindex]!=nil,
							{beatbuttons[index][subindex].value = 1},
							{beatbuttons[index][subindex].value = 0}
						);
					});
				});
			} {|error|
				["setting beat buttons error", error, ~allowedbeats].postln;
				beatbuttons[1][2].value = 1; // emergency activate this one
			};
/*			beatbuttons.do({arg but, i;
				if (~allowedbeats[i]!=nil, {but.value = 1}, {but.value = 0});
			});*/

			~pulse = data[\pulse];
			pulseBut.value = ~pulse;

			~emphasis = data[\emphasis];
			emphasisBut.value = ~emphasis;

			~classictxakun = data[\classictxakun];
			classicBut.value = ~classictxakun;

			~enabled = data[\enabled];
			enabledButs[0].value = ~enabled[0];
			enabledButs[1].value = ~enabled[1];
			// txakun-errena buttons
			~autopilotrange = data[\autopilotrange]; // no widget!

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
			data.put(\emphasis, ~emphasis);
			data.put(\classictxakun, ~classictxakun);
			data.put(\enabled, ~enabled);
			data.put(\autopilotrange, ~autopilotrange);
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

			//data.postln;

			newpreset.string = ""; //clean field
		});

	};


	//s.waitForBoot({
	// Now position all different groups of GUI elements
	doWindow.value(700, 620, "Txalaparta. www.ixi-audio.net");
	doTimeControls.value(2, 5);
	doMakilas.value(440, 5, 8);
	doPlanks.value(10, 150);
	doButtons.value(10, 350);
	doPresets.value(10, 490);


	if (~verbose>0, {currentEnvironment.postln});
	if (~verbose>0, {buffers});
	//});

}); // this to be able to run from command line sclang txalaparta.sc

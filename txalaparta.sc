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
var window, output, slidersauto, makilasliders, nextautopilot, sndpath, samples, buffers, istheresomething, findIndex, presets;
// GUI widgets
var sliders, beatbuttons, classicBut, emphasisBut, pulseBut, planksMenus, ampBut, playBut, enabledButs;
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
~enabled = [true, true]; // switch on/off txakun and errena
~allowedbeats = [0, 1, 2, nil, nil]; // 0,1,2,3,4
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

buffers = [[nil, true], [nil, false], [nil, false], [nil, false]]; // [Buffer, enabled] // FIRST IS ENABLED!

presetspath = thisProcess.nowExecutingPath.dirname ++ "/presets/";
presets = (presetspath++"*").pathMatch;

beatbuttons = Array.fill(5, nil);
sliders = Array.fill(4, [nil,nil]); // slider and its autopilot button associated
slidersauto = Array.fill(4, nil); // keep a ref to the ones available for autopilot
makilasliders = [[nil, nil], [nil, nil]]; // two for each player
planksMenus = Array.fill(4, [nil,nil]);// [Buffer, enable] for each
enabledButs = [nil, nil]; // txakun and errena

drawingSet = Array.fill(4, [0,false]); // delay time from pulse and txakun or not?


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

	var firstdefer=nil, drawingSetB = Array.fill(8, [0, false]); // buffer

	numbeats.do({ arg index; // for each makila one hit
		var hittime, hitfreq, hitswing, hitamp, makilaindex, plank=[nil, false];
		if (~amp > 0, { // emphasis on first or on last hit?
			if ((numbeats == (index+1) && ~emphasis[1]) || (index == 0 && ~emphasis[0]),
				{hitamp = localamp + 0.30},
				{hitamp = localamp}
			);
		});

		{ plank[1] == false }.while( { plank = buffers.choose }); // avoid nil
		if (~verbose>2, {("plank"+plank).postln});

		hitfreq = (~freqs.choose) + 0.6.rand; // just a small freq swing to give some life

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

		if (firstdefer == nil, {firstdefer=hittime}); // to schedule drawing later

		{
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
};








// TXALAPARTA ////////////////////
playF = Routine({
	var txakun; // classical txakun only is limited to two beats
	var intermakilaswing, localstep, idealtempo, localtemposwing, localamp;

	txakun = true; // play starts with txakun
	nextautopilot = 0;

	inf.do({ arg stepcounter; // txakun > errena cycle
		var numbeats, outstr; // numbeats needs to be here to be nill each time

		if (~verbose>1, {("is txakun?" + txakun).postln});

		// autopilot
		if (( istheresomething.value(slidersauto) && (stepcounter >= nextautopilot)) , {
			var sl;
			slidersauto.postln;
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

		// beats
		if ( (txakun && ~enabled[0]) || (txakun.not && ~enabled[1]), // enabled?
		{
			if (~classictxakun && txakun, // choose num of beats for this step
				{ numbeats = 2 },
				{ { numbeats == nil }.while( {numbeats = ~allowedbeats.wchoose(~beatchance)} ) } // avoid nil
			);

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

	window = Window(caption, Rect(100, 100, width, height));
	window.alwaysOnTop = true;
	window.onClose = {AppClock.clear;SystemClock.clear};
	window.front;

	window.drawFunc = {
		var dur, dpt; // duration of the circle and degrees per time unit
		dur = 120/~tempo; // duration of the cycle in secs
		dpt = 360/dur; // how many degrees each ms

		Pen.translate(380, 375);
		Pen.color = Color.black;

		Pen.addArc(0@0, 40, 0, 360);

		if (~pulse.not, {
			Pen.rotate( (((drawingSet[0][0]*dpt)))*(pi/180) );
		});

		Pen.line(0@45.neg, 0@45); //vertical line
		Pen.perform(\stroke); // static if maintaining pulse

		drawingSet.do({arg data; // --> [msecs, txakunflag]
			var offset;
			if (data[0]>0, { // only the ones with a valid value
				//["drawing data was", data].postln;
				if (data[1], {offset = 270}, {offset = 90}); // txakun up, errena down
				Pen.use{
					Pen.rotate( (((data[0]*dpt)-offset)*(pi/180)) );
					Pen.addArc((40)@(0), 5, 0, 360);
					Pen.stroke;
				};
			});
		});

		Pen.perform(\stroke);

		drawingSet = Array.fill(4, [0, false]);//clear
	};
	window.refresh;

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
		ControlSpec(30, 450, \lin, 1, ~tempo, "BPMs"),     // controlSpec
		{ arg ez;
			~tempo = ez.value;
		},
		initVal: ~tempo,
		labelWidth: 80;
	);

	sliders[0][1] = Button(window, Rect(buttonxloc,yloc,60,20))
	.states_([
		["autopilot", Color.white, Color.black],
		["autopilot", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean,{slidersauto[0]=sliders[0][0]}, {slidersauto[0]=nil});
	})
	.valueAction_(0);

	// swing //
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
		["autopilot", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean,{slidersauto[1]=sliders[1][0]}, {slidersauto[1]=nil});
	})
	.valueAction_(0);


	// gap //
	yloc = yloc+gap;
	sliders[3][0] = EZSlider( window,         // parent
		Rect(xloc,yloc,width,20),    // bounds
		"gap",  // label
		ControlSpec(0.001, 1, \lin, 0.001, ~gap, "ms"),     // controlSpec
		{ arg ez;
			~gap = ez.value;
		},
		initVal: ~gap,
		labelWidth: 80;
	);

	sliders[3][1] = Button(window, Rect(buttonxloc,yloc,60,20))
	.states_([
		["autopilot", Color.white, Color.black],
		["autopilot", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean, {slidersauto[3]=sliders[3][0]}, {slidersauto[3]=nil});
	})
	.valueAction_(0);

	// gap swing //
	yloc = yloc+gap;
	sliders[2][0] = EZSlider( window,         // parent
		Rect(xloc,yloc,width,20),    // bounds
		"gap swing",  // label
		ControlSpec(0.001, 1, \lin, 0.001, ~gapswing, "ms"),     // controlSpec
		{ arg ez;
			~gapswing = ez.value;
		},
		initVal: ~gapswing,
		labelWidth: 80;
	);

	sliders[2][1] = Button(window, Rect(buttonxloc,yloc,60,20))
	.states_([
		["autopilot", Color.white, Color.black],
		["autopilot", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean,{slidersauto[2]=sliders[2][0]}, {slidersauto[2]=nil});
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
		["maintain pulse", Color.black, Color.red],
	])
	.action_({ arg butt;
		~pulse = butt.value.asBoolean;
	});

	// EMPHASIS
	emphasisBut = Button(window, Rect(xloc+100,yloc,100,25))
	.states_([
		["last emphasis", Color.white, Color.black],
		["last emphasis", Color.black, Color.red],
	])
	.action_({ arg butt;
		~emphasis = [butt.value.asBoolean.not, butt.value.asBoolean];
	})
	.valueAction_(1);


	// CLASSIC TXAKUN
	classicBut = Button(window, Rect(beatsxloc,yloc+25,100,25))
	.states_([
		["classic txakun", Color.white, Color.black],
		["classic txakun", Color.black, Color.red],
	])
	.action_({ arg butt;
		~classictxakun = butt.value.asBoolean;
	});
	classicBut.valueAction = 1;


	// BEATS
	StaticText(window, Rect(beatsxloc, yloc-16, 200, 20)).string = "Allowed beats";

	beatbuttons[0] = Button(window, Rect(beatsxloc,yloc,20,25))
	.states_([
		["0", Color.white, Color.black],
		["0", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean, {~allowedbeats[0]=0},{~allowedbeats[0]=nil});
	});
	beatbuttons[0].valueAction = 1;

	beatbuttons[1] = Button(window, Rect(beatsxloc+20,yloc,20,25))
	.states_([
		["1", Color.white, Color.black],
		["1", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean, {~allowedbeats[1]=1},{~allowedbeats[1]=nil});
	});
	beatbuttons[1].valueAction = 1;

	beatbuttons[2] = Button(window, Rect(beatsxloc+40,yloc,20,25))
	.states_([
		["2", Color.black, Color.red],
	])
	.action_({ arg butt;}); // NO ACTION. THIS IS ALWAYS ON
	beatbuttons[2].valueAction = 1;

	beatbuttons[3] = Button(window, Rect(beatsxloc+60,yloc,20,25))
	.states_([
		["3", Color.white, Color.black],
		["3", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean, {~allowedbeats[3]=3},{~allowedbeats[3]=nil});
	});
	beatbuttons[3].valueAction = 0;

	beatbuttons[4] =  Button(window, Rect(beatsxloc+80,yloc,20,25))
	.states_([
		["4", Color.white, Color.black],
		["4", Color.black, Color.red],
	])
	.action_({ arg butt;
		if (butt.value.asBoolean, {~allowedbeats[4]=4},{~allowedbeats[4]=nil});
	});
	beatbuttons[4].valueAction = 0;

	// MODE
	Button(window, Rect(xloc,yloc+25,100,25))
	.states_([
		["old pulse", Color.white, Color.black],
		["old pulse", Color.black, Color.red],
	])
	.action_({ arg butt;
		~mode = butt.value.asBoolean;
	}).valueAction = 1;



	// PLAY
	playBut = Button(window, Rect(xloc,yloc+50,200,25))
	.states_([
		["play/stop", Color.white, Color.black],
		["play/stop", Color.black, Color.red],
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

	// ZAHARRA MODE
	Button(window, Rect(beatsxloc,yloc+65,100,25))
	.states_([
		["go zaharra", Color.white, Color.black],
	])
	.action_({ arg butt;
		~classictxakun = true; //butt.value.asBoolean.not;
		beatbuttons.do({arg but, ind;
			if ( but != nil, {
				if ( ind < 2, {but.valueAction = 1}, {but.valueAction = 0});
				classicBut.valueAction = 1;
				emphasisBut.valueAction = 1;
				pulseBut.valueAction = 0
			});
		});
	})
	.valueAction_(1);
};





// PLANKS - OHOLAK //////////////////////////////////
doPlanks = { arg xloc=10, yloc = 260;
	var menuxloc = xloc + 22, playxloc = menuxloc+250+2;

	StaticText(window, Rect(xloc, yloc-18, 200, 20)).string = "Oholak/Planks";

//1
	planksMenus[0][0] = Button(window, Rect(xloc,yloc,20,20))
        .states_([
            ["1", Color.black, Color.red],
        ])
        .action_({ arg butt; }); // NO ACTION. THIS IS ALWAYS ON

	planksMenus[0][1] = PopUpMenu(window,Rect(menuxloc,yloc,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
	         buffers[0][0] = Buffer.read(s, sndpath ++ menu.item);
	         ("loading" + menu.item + "with bufnum" + buffers[0][0].bufnum).postln;
	     })
	.valueAction_(0);
	Button(window, Rect(playxloc,yloc,20,20))
    .states_([
		[">", Color.white, Color.black]
    ])
    .action_({ arg butt;
		Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, buffers[0][0].bufnum])
    });
//2
	planksMenus[1][0] = Button(window, Rect(xloc,yloc+20,20,20))
        .states_([
            ["2", Color.white, Color.black],
            ["2", Color.black, Color.red],
        ])
        .action_({ arg butt;
	       buffers[1][1] = butt.value.asBoolean;
        });
	planksMenus[1][1] = PopUpMenu(window,Rect(menuxloc,yloc+20,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
	           buffers[1][0] = Buffer.read(s, sndpath ++ menu.item);
	           ("loading" + menu.item + "with bufnum" + buffers[1][0].bufnum).postln;
	     })
	.valueAction_(1);
	Button(window, Rect(playxloc,yloc+20,20,20))
    .states_([
		[">", Color.white, Color.black]
    ])
    .action_({ arg butt;
		Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, buffers[1][0].bufnum])
    });

//3
	planksMenus[2][0] = Button(window, Rect(xloc,yloc+40,20,20))
        .states_([
            ["3", Color.white, Color.black],
            ["3", Color.black, Color.red],
        ])
        .action_({ arg butt;
             buffers[2][1] = butt.value.asBoolean;
        });
	planksMenus[2][1] = PopUpMenu(window,Rect(menuxloc,yloc+40,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
	           buffers[2][0] = Buffer.read(s, sndpath ++ menu.item);
	           ("loading" + menu.item + "with bufnum" + buffers[2][0].bufnum).postln;
	     })
	.valueAction_(2);
	Button(window, Rect(playxloc,yloc+40,20,20))
    .states_([
		[">", Color.white, Color.black]
    ])
    .action_({ arg butt;
		Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, buffers[2][0].bufnum])
    });
//4
	planksMenus[3][0] = Button(window, Rect(xloc,yloc+60,20,20))
        .states_([
            ["4", Color.white, Color.black],
            ["4", Color.black, Color.red],
        ])
        .action_({ arg butt;
             buffers[3][1] = butt.value.asBoolean;
        });
	planksMenus[3][1] = PopUpMenu(window,Rect(menuxloc,yloc+60,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
	           buffers[3][0] = Buffer.read(s, sndpath ++ menu.item);
	           ("loading" + menu.item + "with bufnum" + buffers[3][0].bufnum).postln;
	     })
	.valueAction_(3);
	Button(window, Rect(playxloc,yloc+60,20,20))
    .states_([
		[">", Color.white, Color.black]
    ])
    .action_({ arg butt;
		Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, buffers[3][0].bufnum])
    });
};



// MAKILAS
doMakilas = { arg xloc=300, yloc=190, gap=45;
	var ind = 0, thegap = 0;

	output = StaticText(window, Rect(xloc, yloc, 200, 20));
	yloc = yloc + 18;

	makilasliders.do({arg list;
		//thegap.postln;
		list.do({arg item, i;
			list[i] = Slider(window, Rect(xloc+thegap+(21*ind), yloc, 20, 150));
			list[i].orientation = \vertical;
			list[i].thumbSize = 80;
			list[i].value = 1;
			ind = ind + 1;
		});
		thegap = gap;
	});

	// TXAKUN
	enabledButs[0] = Button(window, Rect(xloc,yloc+150,50,25))
	.states_([
		["txakun", Color.white, Color.black],
		["txakun", Color.black, Color.red],
	])
	.action_({ arg butt;
		~enabled[0] = butt.value.asBoolean;
	})
	.valueAction_(1);

	// ERRENA
	enabledButs[1] = Button(window, Rect(xloc+50,yloc+150,50,25))
	.states_([
		["errena", Color.white, Color.black],
		["errena", Color.black, Color.red],
	])
	.action_({ arg butt;
		~enabled[1] = butt.value.asBoolean;
	})
	.valueAction_(1);
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
		(presetspath ++ menu.item).postln;
		 data = Object.readArchive(presetspath ++ menu.item);
		 data.asCompileString.postln;

		~tempo = data[\tempo];
		sliders[0][0].value = ~tempo;
		sliders[0][1].value = data[\slidersauto][0];
		if (data[\slidersauto][0]==true,
			{slidersauto[0]=sliders[0][0]}, {slidersauto[0]=nil});

		~swing = data[\swing];
		sliders[1][0].value = ~swing;
		sliders[1][1].value = data[\slidersauto][1];
		if (data[\slidersauto][1]==true,
			{slidersauto[1]=sliders[1][0]}, {slidersauto[1]=nil});

		~gap = data[\gap];
		sliders[3][0].value = ~gap;
		sliders[3][1].value = data[\slidersauto][2];
		if (data[\slidersauto][3]==true, //yes I know...
			{slidersauto[3]=sliders[3][0]}, {slidersauto[3]=nil});

		~gapswing = data[\gapswing];
		sliders[2][0].value = ~gapswing;
		sliders[2][1].value = data[\slidersauto][3];
		if (data[\slidersauto][2]==true, // //yes I know, I know...
			{slidersauto[2]=sliders[2][0]}, {slidersauto[2]=nil});

		~amp = data[\amp];
		ampBut.value = ~amp;

		~allowedbeats = data[\allowedbeats];
		beatbuttons.do({arg but, i;
			if (~allowedbeats[i]!=nil, {but.value = 1}, {but.value = 0});
		});

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
			plank[0].valueAction = data[\buffers][i][1];
			plank[1].valueAction = findIndex.value(plank[1], data[\buffers][i][0]);
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
		data.put(\buffers, [
			[ buffers[0][0].path, buffers[0][1] ],
			[ buffers[1][0].path, buffers[1][1] ],
			[ buffers[2][0].path, buffers[2][1] ],
			[ buffers[3][0].path, buffers[3][1] ],
		]);
		data.writeArchive(presetspath++filename);

		newpreset.string = ""; //clean field
	});

};


//s.waitForBoot({
	// Now position all different groups of GUI elements
	doWindow.value(435, 420, "Txalaparta. www.ixi-audio.net");
	doTimeControls.value(2, 5);
	doButtons.value(10, 250);
	doPlanks.value(10, 150);
	doMakilas.value(330, 130, 16);
	doPresets.value(10, 370);


	if (~verbose>0, {currentEnvironment.postln});
	if (~verbose>0, {buffers});
//});

}); // this to be able to run from command line sclang txalaparta.sc


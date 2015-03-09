/*
txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta

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


// this is to avoid problem in some windows machines
/*Platform.case(
	//\osx,       { "OSX".postln },
	\linux,     { Server.default = Server.internal },
	\windows,   { Server.default = Server.internal }
);*/
(

s.boot; //////// BOOT SERVER //////////////////

s.doWhenBooted({

	var presetspath, makilaF, drawingSetBuffer, midioutdev, txalatempo, currentpath; //

	// GUI vars
	var window, timelinewin, clock, nextautopilot, sndpath, samples, istheresomething, findIndex, presets, tscore;
	// GUI widgets
	var beatButtons, beatSliders, oldpulseBut, emphasisBut, zerolimitBut, pulseBut, ampBut, autoplayBut, interactiveplayBut,timecontrols, plankcontrols;
	// GUI functions vars
	var doWindow, doMakilas, doButtons, doPresets, doTxalaScore, postOutput, doControPanel, drawHitSet;

	currentpath = thisProcess.nowExecutingPath.dirname;

	~txalaparta = Txalaparta.new( s, currentpath );

	presetspath = currentpath ++ "/presets/";
	presets = (presetspath++"*").pathMatch;

	beatButtons = [Array.fill(5, {nil}), Array.fill(5, {nil})];
	beatSliders = Array.fill(5, {nil});

	~txalascore = TxalaScoreGUI.new;

	/* check if any of the values in an array is not nil
	*/
	istheresomething = {arg alist;
		var values = List[];
		alist.do({arg item;
			if (item!=nil, {values.add(true)});
		});
		if(values.size>0, {true}, {false});
	};

	////////////////////////////////////////////////
	// GUI /////////////////////////////////////////
	////////////////////////////////////////////////


	/* returns true if any of the items in the array or list is not nil
	*/
	findIndex = {arg plankmenu, path;
		var returnval=0;
		plankmenu.items.do({arg file, i;
			if (sndpath++file==path,{returnval = i});
		});
		returnval;
	};




	// WINDOW
	doWindow = {arg width, height, caption;
		//var rot=0;

		window = Window(caption, Rect(100, 100, width, height));
		//window.alwaysOnTop = true;
		window.onClose = {
			if (txalatempo.isNil.not, {txalatempo.closeGUI()});
			if (~makilaanims.isNil.not, {~makilaanims.close()});
			if (~txalascore.isNil.not, {~txalascore.close});
			AppClock.clear;
			SystemClock.clear;

		};
		window.front;

/*		window.drawFunc = { // drawing the visualization of circles
			var dur, dpt; // duration of the circle and degrees per time unit
			dur = 60/~tempo; // duration of the cycle in secs
			dpt = 360/dur; // how many degrees each ms

			Pen.translate(565, 520); // location of the circle
			Pen.color = Color.black;
			Pen.addArc(0@0, 80, 0, 360);
			Pen.line(10@90.neg, 15@87.neg); // > mark
			Pen.line(15@87.neg, 10@84.neg);

			Pen.perform(\stroke); // static if maintaining pulse

			if (~pulse.not, {

				try {
					rot = rot + (((~makilaanims.drawingSetBuffer[1][0][0]*dpt)))*(pi/180); // apply the rotation of the current beat
				} {|error| rot = 0};
				Pen.rotate( rot )
			});
			Pen.line(0@90.neg, 0@90); //vertical line
			Pen.perform(\stroke); // static if maintaining pulse

			//drawHitSet.value(drawingSetBuffer[0], dur, dpt);

			//Pen.rotate( rot );

			drawHitSet.value(~makilaanims.drawingSetBuffer[1], dur, dpt);

			~makilaanims.drawingSetBuffer = [ ~makilaanims.drawingSetBuffer[1], Array.fill(4, {[0, -1, false, 10]}) ];

		};*/
	};

	// drawHitSet = { arg set, dur, dpt;
	// 	set.do({arg data; // --> [msecs, txakunflag, amp]
	// 		var offset;
	//
	// 		if (data[3] <= 1, { // only the ones with a valid data
	// 			if (data[2], {//txakun
	// 				offset = 270;
	// 				Pen.color = Color.red; //.alpha_(0.8).set;
	// 				},{
	// 					offset = 90;
	// 					Pen.color = Color.blue;
	// 			}); // txakun up, errena down
	//
	// 			Pen.use{
	// 				Pen.rotate( (((data[1]*dpt)-offset)*(pi/180)) );
	// 				Pen.addArc((80)@(0), data[3]*12, 0, 360); // circles representing beats
	// 				Pen.perform(\fill);
	// 			};
	// 		});
	// 	});
	// };




	// BOTONES
	doButtons = { arg xloc=10, yloc = 110;
		var beatsxloc = 220;

		// PULSE
		pulseBut = Button(window, Rect(xloc,yloc,100,25))
		.states_([
			["maintain pulse", Color.white, Color.black],
			["maintain pulse", Color.black, Color.green],
		])
		.action_({ arg butt;
			~pulse = butt.value.asBoolean;
		})
		.valueAction_(~pulse.asInt);

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
			beatButtons.do({arg butset;
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

				beatButtons[index][subindex] = Button(window, Rect(beatsxloc+(20*index),yloc+(25*subindex),20,25))
				.states_([
					[subindex.asString, Color.white, Color.black],
					[subindex.asString, Color.black, thecolor],
				])
				.action_({ arg butt;
					if (butt.value.asBoolean,
						{~allowedbeats[index][subindex] = subindex},
						{~allowedbeats[index][subindex] = nil});
				});
				beatButtons[index][subindex].valueAction = 0;
			});

			beatSliders[subindex] = Slider(window,
				Rect(beatsxloc+40,yloc+(25*subindex),75,25))
			.action_({arg sl;
				~beatchance[subindex] = sl.value;
			}).orientation = \horizontal;
			beatSliders[subindex].valueAction = ~beatchance[subindex];

			Button(window, Rect(beatsxloc+115,yloc+(25*subindex),20,20))
			.states_([
				["P", Color.white, Color.black],
			])
			.action_({ arg butt;
				ParamWin.new("~beatchance["++subindex++"]", ControlSpec(0.001, 1), beatSliders[subindex]);
			});
		});

		beatButtons[0][2].valueAction = 1; // activate by default
		beatButtons[1][2].valueAction = 1;


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
			["show score", Color.white, Color.black],
		])
		.action_({ arg butt;
			var num;
			num = ~txalaparta.getnumactiveplanks();
			~txalascore.doTxalaScore(numactiveplanks:num);
		});

		// txakascore timeline
		Button(window, Rect(beatsxloc,yloc+200,100,25))
		.states_([
			["show animation", Color.white, Color.black],
		])
		.action_({ arg butt;
			if (~makilaanims.isNil, {
				~makilaanims = TxalaDisplayGraphics.new( 10 , 10, 270, 620, 10)
			});
		});

		// MODE
		oldpulseBut = Button(window, Rect(xloc,yloc+25,100,25))
		.states_([
			["old pulse", Color.white, Color.black],
			["old pulse", Color.black, Color.green],
		])
		.action_({ arg butt;
			~mode = butt.value.asBoolean;
		}).valueAction_(~mode.asInt);



		// TXAKUN
		Button(window, Rect(xloc,yloc+160,100,30))
		.states_([
			["txakun", Color.white, Color.black],
			["txakun", Color.black, Color.red],
		])
		.action_({ arg butt;
			~enabled[0] = butt.value.asBoolean;
		})
		.valueAction_(1);

		// ERRENA
		Button(window, Rect(xloc+100,yloc+160,100,30))
		.states_([
			["errena", Color.white, Color.black],
			["errena", Color.black, Color.blue],
		])
		.action_({ arg butt;
			~enabled[1] = butt.value.asBoolean;
		})
		.valueAction_(1);


		// AUTO PLAY
		autoplayBut = Button(window, Rect(xloc,yloc+190,200,35))
		.states_([
			["auto play", Color.white, Color.black],
			["auto play", Color.black, Color.green],
		])
		.action_({ arg butt;
			if ( butt.value.asBoolean, { ~txalaparta.autoplay() },
				{ ~txalaparta.autostop() });
		});

		// INTERACTIVE PLAY
/*		interactiveplayBut = Button(window, Rect(xloc,yloc+225,200,35))
		.states_([
			["interactive play", Color.white, Color.black],
		])
		.action_({ arg butt;
			txalatempo = TxalaMarkovTempo.new(s, currentpath);
		});*/
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
				PathName.new(item).fileName
			});
		})
		.action_({ arg menu;
			var data, sliders;
			("loading..." + presetspath ++ menu.item).postln;
			data = Object.readArchive(presetspath ++ menu.item);
			data.asCompileString.postln;

			~tempo = data[\tempo];
			~swing = data[\swing];
			~gap = data[\gap];
			~gapswing = data[\gapswing];
			timecontrols.updatesliders();

			//~amp = data[\amp];
			//ampBut.value = ~amp;

			~allowedbeats = data[\allowedbeats];
			if(~allowedbeats.size>2, // backwards compatible with old presets
				{~allowedbeats=[~allowedbeats, [nil,nil,nil,nil,nil]]
			});

			try { //bckwads compatible again
				beatButtons.do({arg playerbuttons, index;
					playerbuttons.do({arg but, subindex;
						but.value = ~allowedbeats[index][subindex].asBoolean.asInt; // 0 or 1
					});
				});
			} {|error|
				["setting beat buttons error", error, ~allowedbeats].postln;
				beatButtons[1][2].value = 1; // emergency activate this one
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

			// txakun-errena buttons
			~autopilotrange = data[\autopilotrange]; // no widget!

			try {
				~plankchance = data[\plankchance];
				plankcontrols.planksChanceMenus.do({arg plank, i;
					plank.valueAction = data[\plankchance][i];
				});
			} {|error|
				"not plankchance in preset".postln;
			};

			try {
				~beatchance = data[\beatchance];
				beatSliders.do({arg beatsl, index;
					beatsl.valueAction = ~beatchance[index];
				});
			} {|error|
				"not beatchance in preset".postln;
			};

			plankcontrols.planksMenus.do({arg plank, i;
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
			data.put(\enabled, ~enabled);
			data.put(\autopilotrange, ~autopilotrange);
			data.put(\beatchance, ~beatchance);
			data.put(\plankchance, ~plankchance);

			data.put(\buffers, [ //path to file, tx flag, err flag
			 	[ ~buffers[0].path, ~buffersenabled[0][0], ~buffersenabled[1][0] ],
			 	[ ~buffers[1].path, ~buffersenabled[0][1], ~buffersenabled[1][1] ],
			 	[ ~buffers[2].path, ~buffersenabled[0][2], ~buffersenabled[1][2] ],
			 	[ ~buffers[3].path, ~buffersenabled[0][3], ~buffersenabled[1][3] ],
			 	[ ~buffers[4].path, ~buffersenabled[0][4], ~buffersenabled[1][4] ],
			 	[ ~buffers[5].path, ~buffersenabled[0][5], ~buffersenabled[1][5] ],
			 	[ ~buffers[6].path, ~buffersenabled[0][6], ~buffersenabled[1][6] ],
				[ ~buffers[7].path, ~buffersenabled[0][7], ~buffersenabled[1][7] ],
			 ]);

			data.writeArchive(presetspath++filename);

			newpreset.string = ""; //clean field
		});

	};

	doWindow.value(430, 580, "Txalaparta. www.ixi-audio.net");
	timecontrols = TxalaTimeControls.new(window);
	plankcontrols = TxalaPlankControls.new(window, 10,160, 400, 20,
		~txalaparta.samples.asArray.collect({arg item; PathName.new(item).fileName}));

	doButtons.value(10, 350);
	doPresets.value(10, 430);

	if (~verbose>0, {currentEnvironment.postln});
	if (~verbose>0, {~buffers});

}); // this to be able to run from command line sclang txalaparta.sc

)
TxalaAuto{
	var presetspath, txalatempo, currentpath; //

	// GUI vars
	var window, timelinewin, clock, nextautopilot, samples, presets, tscore;
	// GUI widgets
	var beatButtons, beatSliders, oldpulseBut, emphasisBut, zerolimitBut, pulseBut, ampBut, autoplayBut, interactiveplayBut,timecontrols, plankcontrols, numplanks;
	var server;
	// GUI functions vars
	//var postOutput, doControPanel, drawHitSet;

	*new {| aserver, apath="" |
		^super.new.initTxalaAuto(aserver, apath);
	}

	initTxalaAuto { arg aserver, apath;

		server = aserver;

		currentpath = apath;

		numplanks = 6;

		~txalaparta = Txalaparta.new( server, currentpath, numplanks );
		~txalaparta.loadsampleset("0salazar4");//????????????

		presetspath = currentpath ++ "/presets/";
		presets = (presetspath++"*").pathMatch;

		beatButtons = [Array.fill(5, {nil}), Array.fill(5, {nil})];
		beatSliders = Array.fill(5, {nil});

		~txalascoreAuto = TxalaScoreGUI.new;


		this.doWindow(390, 380, "Txalaparta. www.ixi-audio.net");

		timecontrols = TxalaTimeControls.new(window, path:currentpath);
		plankcontrols = TxalaPlankControls.new(window, 220, 250, 400, 20, numplanks,
			~txalaparta.samples.asArray.collect({arg item; PathName.new(item).fileName}), currentpath); // pass file names to GUI

		this.doButtons(10, 130);
		this.doPresets(10, 280);
		this.doPlanksSetGUI(window, 10, 330);

		if (~verbose>0, {currentEnvironment.postln});
		if (~verbose>0, {~buffersATX});

	}


	kill {
		//this.stop();
		window.close;
	}

	/* check if any of the values in an array is not nil
	*/
	istheresomething {arg alist;
		var values = List[];
		alist.do({arg item;
			if (item!=nil, {values.add(true)});
		});
		if(values.size>0, {true}, {false});
	}

	////////////////////////////////////////////////
	// GUI /////////////////////////////////////////
	////////////////////////////////////////////////


	/* returns true if any of the items in the array or list is not nil
	*/
	findIndex {arg plankmenu, path;
		var returnval=0;
		plankmenu.items.do({arg file, i;
			if (file==path.fileName,{returnval = i});
		});
		returnval;
	}




	// WINDOW
	doWindow {arg width, height, caption;
		//var rot=0;
		window = Window(caption, Rect(100, 100, width, height));
		//window.alwaysOnTop = true;
		window.onClose = {
			if (txalatempo.isNil.not, {txalatempo.closeGUI()});
			if (~makilaanims.isNil.not, {~makilaanims.close()});
			if (~txalascoreAuto.isNil.not, {~txalascoreAuto.close});
			AppClock.clear;
			SystemClock.clear;

		};
		window.front;

	}


	// BOTONES
	doButtons { arg xloc=10, yloc = 110;
		var beatsxloc = 10;

/*		// PULSE
		pulseBut = Button(window, Rect(xloc,yloc,100,25))
		.states_([
			["maintain pulse", Color.white, Color.black],
			["maintain pulse", Color.black, Color.green],
		])
		.action_({ arg butt;
			~pulse = butt.value.asBoolean;
		})
		.valueAction_(~pulse.asInt);*/



		// txakascore timeline
		Button(window, Rect(xloc,yloc,100,30))
		.states_([
			["show score", Color.white, Color.black],
		])
		.action_({ arg butt;
			//var num;
			//num = ~txalaparta.getnumactiveplanks(); // TO DO: fix to numplanks for new buffer system??
			~txalascoreAuto.reset();
			~txalascoreAuto.doTxalaScore(numactiveplanks:numplanks);
		});

		// txakascore timeline
		Button(window, Rect(xloc+100,yloc,100,30))
		.states_([
			["show animation", Color.white, Color.black],
		])
		.action_({ arg butt;
			if (~makilaanims.isNil, {
				~makilaanims = TxalaDisplayGraphics.new( 450 , 10)
			});
		});


		/*		// ZAHARRA MODE
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
		.valueAction_(1);*/


		// BEATS
		StaticText(window, Rect(beatsxloc+210, yloc-20, 200, 20)).string = "Hits";
		StaticText(window, Rect(beatsxloc+260, yloc-20, 200, 20)).string = "% chance";

		~allowedbeats[0].size.do({arg subindex;
			2.do({arg index; // two players
				var thecolor;
				if (index%2==0, {thecolor=Color.red}, {thecolor=Color.blue});

				beatButtons[index][subindex] = Button(window, Rect(beatsxloc+210+(20*index),yloc+(20*subindex),20,20))
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
				Rect(beatsxloc+40+210,yloc+(20*subindex),75,20))
			.action_({arg sl;
				~beatchance[subindex] = sl.value;
			}).orientation = \horizontal;
			beatSliders[subindex].valueAction = ~beatchance[subindex];

			Button(window, Rect(beatsxloc+210+115,yloc+(20*subindex),20,20))
			.states_([
				["P", Color.white, Color.black],
			])
			.action_({ arg butt;
				ParamWin.new("~beatchance["++subindex++"]", ControlSpec(0.001, 1), beatSliders[subindex], presetspath:currentpath);
			});
		});

		beatButtons[0][2].valueAction = 1; // activate by default
		beatButtons[1][2].valueAction = 1;



/*		// MODE
		oldpulseBut = Button(window, Rect(xloc,yloc+25,100,25))
		.states_([
			["old pulse", Color.white, Color.black],
			["old pulse", Color.black, Color.green],
		])
		.action_({ arg butt;
			~mode = butt.value.asBoolean;
		}).valueAction_(~mode.asInt);*/

		// TXAKUN
		Button(window, Rect(xloc,yloc+32,100,30))
		.states_([
			["txakun", Color.white, Color.black],
			["txakun", Color.black, Color.red],
		])
		.action_({ arg butt;
			~enabled[0] = butt.value.asBoolean;
		})
		.valueAction_(1);

		// ERRENA
		Button(window, Rect(xloc+100,yloc+32,100,30))
		.states_([
			["errena", Color.white, Color.black],
			["errena", Color.black, Color.blue],
		])
		.action_({ arg butt;
			~enabled[1] = butt.value.asBoolean;
		})
		.valueAction_(1);


		// AUTO PLAY
		autoplayBut = Button(window, Rect(xloc,yloc+64,200,35))
		.states_([
			["play", Color.white, Color.black],
			["play", Color.black, Color.green],
		])
		.action_({ arg butt;
			if ( butt.value.asBoolean,
				{ ~txalaparta.autoplay() },
				{ ~txalaparta.autostop() });
		});

		// EMPHASIS
		emphasisBut = Button(window, Rect(xloc,yloc+105,100,25))
		.states_([
			["last emphasis", Color.white, Color.black],
			["last emphasis", Color.black, Color.green],
		])
		.action_({ arg butt;
			~lastemphasis = butt.value.asBoolean;
		})
		.valueAction_(1);
	}




	doPresets { arg xloc, yloc;
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
			//data.asCompileString.postln;

			~tempo = data[\tempo];
			~swing = data[\swing];
			~gap = data[\gap];
			~gapswing = data[\gapswing];
			timecontrols.updatesliders();

			~allowedbeats = data[\allowedbeats];
/*			if(~allowedbeats.size>2, // backwards compatible with old presets
				{~allowedbeats=[~allowedbeats, [nil,nil,nil,nil,nil]]
			});*/

			//try { //bckwads compatible
			beatButtons.do({arg playerbuttons, index;
				playerbuttons.do({arg but, subindex;
					but.value = ~allowedbeats[index][subindex].asBoolean.asInt; // 0 or 1
				});
			});
	/*		} {|error|
				["setting beat buttons error", error, ~allowedbeats].postln;
				beatButtons[1][2].value = 1; // emergency activate this one
			};*/

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
					plank[0].valueAction = data[\buffers][0][i].asInt;
				} {|error|
					plank[0].valueAction = 0;
					["catch plank0 error", error, i].postln;
				};

				try {
					plank[1].valueAction = data[\buffers][1][i].asInt;// set er button
				} {|error|
					plank[1].valueAction = 0;
					["catch plank1 error", error, i].postln;
				};
			});

		});

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
			//data.put(\pulse, ~pulse);
			data.put(\emphasis, ~lastemphasis);
			data.put(\enabled, ~enabled);
			data.put(\autopilotrange, ~autopilotrange);
			data.put(\beatchance, ~beatchance);
			data.put(\plankchance, ~plankchance);
			data.put(\buffers, ~buffersenabled);

			data.writeArchive(presetspath++filename);

			newpreset.string = ""; //clean field
		});
	}


	updatesamplesetpresetfiles{
		var temp, names;
		temp = (currentpath++"/sounds/*").pathMatch; // update
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


	doPlanksSetGUI { arg win, xloc, yloc;
		var newpreset, popup;

		StaticText(win, Rect(xloc, yloc, 170, 20)).string = "Plank set";

		yloc = yloc+20;
/*
		Button(win,  Rect(xloc, yloc,80,25))
		.states_([
			["sample new", Color.white, Color.grey],
		])
		.action_({ arg butt;
			TxalaSet.new(server, ~txalaparta.sndpath)
		});*/

		//yloc = yloc+27;

		popup = PopUpMenu(win,Rect(xloc,yloc,170,20))
		.items_( this.updatesamplesetpresetfiles() )
		.mouseDownAction_( { arg menu;
			menu.items = this.updatesamplesetpresetfiles();
		} )
		.action_({ arg menu;
			~txalaparta.loadsampleset(menu.item);
		});

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset **
			popup.valueAction_(1);
		}{ |error|
			"no predefined plank preset to be loaded".postln;
			error.postln;
		};
	}
}
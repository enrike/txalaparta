// license GPL
// by www.ixi-audio.net

/*
w = Window.new("", Rect(0, 100, 400, 400));
TxalaPlankControls.new(w, 0,0,200,200, []);
w.front;
*/



TxalaPlankControls {

	var win, buttonxloc, <planksMenus, <planksChanceMenus, samples, xloc, yloc, width, gap, currentpath;
	var numplanks;

	*new { | awin, ax=10 ay=160, aw=400, agap=20, anumplanks=8, somesamples, path |
		^super.new.initTxalaPlankControls(awin, ax, ay, aw, agap, anumplanks, somesamples, path);
	}

	initTxalaPlankControls { |awin, ax, ay, aw, agap, anumplanks, somesamples, apath|
		var menuxloc = ax + 44;
		var playxloc = menuxloc;


		//[awin, ax, ay, aw, agap, somesamples].postln;

		win = awin;
		xloc = ax;
		yloc = ay;
		width = aw;
		gap = agap;
		numplanks = anumplanks;
		samples = somesamples;
		currentpath = apath;

		// PLANKS - OHOLAK //////////////////////////////////
/*		StaticText(win, Rect(xloc, yloc-18, 200, 20)).string = "TX";
		StaticText(win, Rect(xloc+22, yloc-18, 200, 20)).string = "ER";*/
		StaticText(win, Rect(xloc, yloc-18, 200, 20)).string = ~txl.do("Planks");
		StaticText(win, Rect(xloc+70, yloc-16, 200, 20)).string = ~txl.do("% chance");

		/*		if (~buffersATX.isNil, {
		~buffersATX = Array.fill(8, {nil});
		});*/

/*		if (~plankchance.isNil, {
			~plankchance = Array.fill(numplanks, {1});
		});*/

		planksMenus = Array.fill(numplanks, {[nil,nil,nil]});
		planksChanceMenus = Array.fill(numplanks, {nil});


		////////////////
		numplanks.do({ arg index;
			//txakun row buttons
			planksMenus[index][0] = Button(win, Rect(xloc,yloc+(gap*index),20,20))
			.states_([
			 	[(index+1).asString, Color.white, Color.black],
			 	[(index+1).asString, Color.black, Color.red],
			 ])
			 .action_({ arg butt;
			 	~buffersenabled[0][index] = butt.value.asBoolean;
			 	//this.updateTxalaScoreNumPlanks();
			 });

			// errena row buttons
			planksMenus[index][1] = Button(win, Rect(xloc+22,yloc+(gap*index),20,20))
			.states_([
				[(index+1).asString, Color.white, Color.black],
				[(index+1).asString, Color.black, Color.blue],
			])
			.action_({ arg butt;
				~buffersenabled[1][index] = butt.value.asBoolean; // [[false...],[false...]]
				//this.updateTxalaScoreNumPlanks();
			});


			if (index==0, {
				planksMenus[index][0].valueAction = 1;
				planksMenus[index][1].valueAction = 1;
			});// ONLY activate first ones


			// menus for each plank
			// TO DO: this needs to be removed when ND sample system is active
/*			planksMenus[index][2] = PopUpMenu(win,Rect(menuxloc,yloc+(gap*index),250,20))
			.items_(samples)
			.action_({ arg menu;
				var item = nil;
				try { // when there is no sound for this
					 item = menu.item;
				} {|error|
					"empty slot".postln;
				};

				if (item.isNil.not, {
					~txalaparta.load(menu.item, index);
				})
			}).valueAction_(index);

			{ // updating value in the menu
				if (~buffersATX[index].isNil.not, { planksMenus[index][2].valueAction_(index) });
			}.defer(1);*/

			// play buttons row
			Button(win, Rect(playxloc,yloc+(gap*index),20,20))
			.states_([
				[">", Color.white, Color.black]
			])
			.action_({ arg butt;// play a single shot
				if (~buffersATXND[index].isNil.not, {
					// this should maybe take the items in the middle [index][mid][mid]
					Synth(\playBuf, [\amp, 0.7, \freq, 1, \bufnum, ~buffersATXND[index][0][0].bufnum])
				})
			});

			// chance sliders
			planksChanceMenus[index] = Slider(win,Rect(menuxloc+20,yloc+(gap*index),75,20))
			.action_({arg sl;
				~plankchance[index] = sl.value;
			})
			.orientation_(\horizontal)
			.valueAction_(1);

			Button(win, Rect(menuxloc+95,yloc+(gap*index),20,20))
			.states_([
				["P", Color.white, Color.black],
			])
			.action_({ arg butt;
				ParamWin.new("~plankchance["++index++"]", ControlSpec(0.001, 1), planksChanceMenus[index], presetspath:currentpath);
			});
		});
	}

/*	updateTxalaScoreNumPlanks {
		var numactiveplanks = ~txalaparta.getnumactiveplanks();
		~txalascoreAuto.updateNumPlanks( numactiveplanks );
	}*/
}

ParamWin {

	var size, lwin, lmsl, lmslline, lbut, lpbut, lfield, lloopf, ldata, varStr, ctrlSp;


	*new {|caption, globalvarStr|
		^super.new.initParamWin( caption, globalvarStr );
	}

	initParamWin {|globalvarStr, aControlSpec|
		varStr = globalvarStr;
		ctrlSp = aControlSpec;
		size = 360 / 6;
		lwin = Window("Control" + varStr,  Rect(0, 0, 360, 135));

		lmsl = MultiSliderView(lwin,  Rect(0, 0, 360, 100));
		lmsl.value_(Array.fill(size, {0}));
		lmsl.isFilled_(true); // width in pixels of each stick
		lmsl.indexThumbSize_(2.0); // spacing on the value axis
		lmsl.gap_(4);
		lmsl.showIndex_(true); // cursor mode

		StaticText(lwin, Rect(3, 110, 100, 20)).string = "Duration";
		lfield = TextField(lwin, Rect(60, 110, 40, 20)).string = "10";

		lloopf = Task {
			inf.do({arg index;
				var val;
				val = ctrlSp.map(lmsl.value.at(lmsl.index)).asStringPrec(3);
				(varStr+"="+val).interpret; // here set value of the variable I control
				ldata.string = varStr ++ ":\n"+ val;
				lmsl.index_(index%lmsl.size); //next step
				(lfield.value.asInt/lmsl.size).wait; // each cycle takes as many secs as the lfield says
			});
		};

		lpbut = Button(lwin, Rect(110,105,80,25))
		.states_([
			["play/pause", Color.white, Color.black],
			["play/pause", Color.black, Color.green],
		])
		.action_({ arg butt;
			if (butt.value.asBoolean, {lloopf.play(AppClock)}, {lloopf.stop});
		});

		Button(lwin,  Rect(190,105,80,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg butt;
			{lloopf.reset}.defer(0.2);  // otherwise wont stop
		});

		ldata = StaticText(lwin, Rect(275, 100, 100, 30)).string = varStr ++ ": \n0";

		lwin.front;
	}

}

/*
~amp=1;
p = ParamWin.new("~amp", ControlSpec(0.01, 2000));
*/

		
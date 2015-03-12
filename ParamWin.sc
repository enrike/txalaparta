/*
a windown with a multislider view that controls over time the value of a global value

ParamWin.new("~whatever", ControlSpec(0.001, 1), presetspath:thisProcess.nowExecutingPath.dirname);

it can also control a slider or some other widget that takes .value
l = Slider(nil,Rect(0,0, 100,20)).action_({arg sl; }).orientation = \horizontal;
ParamWin.new("~whatever", ControlSpec(0.001, 1), l, presetspath:thisProcess.nowExecutingPath.dirname);
*/

ParamWin {

	var size, lwin, lmsl, lmslline, lbut, lpbut, lfield, lloopf, ldata, varStr, ctrlSpc, widget, presetspath, presets;


	*new {|globalvarStr, aControlSpec, aWidget, width=600, height=240, presetspath|
		^super.new.initParamWin( globalvarStr, aControlSpec, aWidget, width, height, presetspath);
	}

	initParamWin {|globalvarStr, aControlSpec, aWidget, width, height, apath|
		var maxv, midv, minv;

		varStr = globalvarStr;
		ctrlSpc = aControlSpec;
		widget = aWidget;
		presetspath = apath ++ "/presets_params/";
		presets = (presetspath++"*").pathMatch;

		size = width / 6;
		lwin = Window("Control" + varStr,  Rect(0, 0, width, height))
		.onClose_({
			lloopf.stop;
		});

		maxv = ctrlSpc.maxval.round(0.1).asString;
		midv = ((ctrlSpc.maxval-ctrlSpc.minval)/2).round(0.1).asString;
		minv = ctrlSpc.minval.round(0.1).asString;

		StaticText(lwin, Rect(3, -2, 20, 20)).string_( maxv ).font_(Font("Monaco", 9));
		StaticText(lwin, Rect(width-17, -2, 20, 20)).string_( maxv ).font_(Font("Monaco", 9));

		StaticText(lwin, Rect(3, (height-80)/2, 20, 20)).string_( midv ).font_(Font("Monaco", 9));
		StaticText(lwin, Rect(width-17, (height-80)/2, 20, 20)).string_( midv ).font_(Font("Monaco", 9));

		StaticText(lwin, Rect(3, height-80, 20, 20)).string_( minv ).font_(Font("Monaco", 9));
		StaticText(lwin, Rect(width-17, height-80, 20, 20)).string_( minv ).font_(Font("Monaco", 9));

		lmsl = MultiSliderView(lwin,  Rect(20, 0, width-40, height-65));
		lmsl.value_(Array.fill(size, {0}));
		lmsl.isFilled_(true); // width in pixels of each stick
		lmsl.indexThumbSize_(2.0); // spacing on the value axis
		lmsl.gap_(4);
		lmsl.showIndex_(true); // cursor mode

		StaticText(lwin, Rect(3, height-55, 100, 20)).string = "Duration";
		lfield = TextField(lwin, Rect(60, height-55, 40, 20)).string = "10";

		lloopf = Task {
			inf.do({arg index;
				var val;
				val = ctrlSpc.map(lmsl.value.at(lmsl.index)).asStringPrec(3);
				(varStr+"="+val).interpret; // here set value of the variable I control
				ldata.string = varStr ++ ": " ++ val;
				if (widget != nil, {widget.value = val}); // update this widget
				lmsl.index_(index%lmsl.size); //next step
				(lfield.value.asInt/lmsl.size).wait; // each cycle takes as many secs as the lfield says
			});
		};

		lpbut = Button(lwin, Rect(110,height-60,80,25))
		.states_([
			["play/pause", Color.white, Color.black],
			["play/pause", Color.black, Color.green],
		])
		.action_({ arg butt;
			if (butt.value.asBoolean, {lloopf.play(AppClock)}, {lloopf.stop});
		});

		Button(lwin,  Rect(190,height-60,80,25))
		.states_([
			["reset", Color.white, Color.black],
		])
		.action_({ arg butt;
			{lloopf.reset}.defer(0.2);  // otherwise wont stop
		});

		ldata = StaticText(lwin, Rect(10, height-30, 400, 30)).string = varStr ++ ": 0";

		this.doPresets(375, height-50);

		lwin.front;
	}

	doPresets { arg xloc, yloc;
		var popupmenu, newpreset;

		StaticText(lwin, Rect(xloc, yloc-16, 200, 20)).string = "Presets";

		PopUpMenu(lwin,Rect(xloc,yloc,200,20))
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

			lmsl.value = data[\array];

		});
		//.valueAction_(0);

		newpreset = TextField(lwin, Rect(xloc, yloc+22, 125, 25));

		Button(lwin, Rect(xloc+130,yloc+22,70,25))
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
			data.put(\array, lmsl.value);

			data.writeArchive(presetspath++filename);

			newpreset.string = ""; //clean field
		});

	}

}

/*
(
~value=20;
 w = Window("main", Rect(0, 0, 300, 100));
l = EZSlider( w,
			Rect(5,5,200,20),
			"gap",
			ControlSpec(20, 1000, \lin, 0.001, ~value, "ms"),
			{ arg ez;
				~amp = ez.value;
			},
			initVal: ~value,
			labelWidth: 80;
		);
w.front;

ParamWin.new("~value", ControlSpec(20, 1000), l);
)
*/

		
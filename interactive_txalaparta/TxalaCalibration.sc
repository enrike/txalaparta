// license GPL
// by www.ixi-audio.net

/*
TxalaCalibration.new(awindow, apath)
*/



TxalaCalibration{

	var win, parent, <>guielements, basepath, helpwin;


	*new { |parent, basepath|
		^super.new.initTxalaCalibration(parent, basepath);
	}

	initTxalaCalibration { |aparent, abasepath|
		parent = aparent;
		basepath = abasepath;
		this.doGUI();
	}

	close {
		win.close();
		if (helpwin.isNil.not, {helpwin.close});
	}

	doGUI {
		var yindex=0, yloc = 10, gap=20, labelwidth=70; //Array.fill(10, {nil});
		var newpreset, popup, presetslisten;
		win = Window(~txl.do("Input calibration"),  Rect(10, 50, 400, 260));
		win.onClose = {
			parent.txalacalibration = nil;
			if (helpwin.isNil.not, {helpwin.close});
		};

		if ( (~txl.lang==1), { labelwidth = 100 });//ES
		if ( (~txl.lang==2), { labelwidth = 130 }); //EU

		guielements = ();

		// calibration //////////////////////////////////////////////

		// ~gain
		guielements.add(\gain-> EZSlider( win,
			Rect(20,yloc+(gap*yindex),370,20),
			~txl.do("gain in"),
			ControlSpec(0, 5, \lin, 0.01, 1, ""),
			{ arg ez;
				~listenparemeters.gain = ez.value.asFloat;
				if (parent.scopesynth.isNil.not, {parent.scopesynth.set(\gain, ez.value.asFloat)});
				if (parent.txalasilence.isNil.not, {
					parent.txalasilence.synth.set(\gain, ez.value.asFloat);
				});
				if (parent.txalaonset.isNil.not, {
					parent.txalaonset.synth.set(\gain, ez.value.asFloat);
				});
			},
			initVal: ~listenparemeters.gain,
			labelWidth: labelwidth
		));

		yindex = yindex + 1;

	guielements.add(\comp_thres->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("comp_thres"),
				ControlSpec(0.01, 1, \lin, 0.01, 0.2, "RMS"),
				{ arg ez;
					if (parent.txalasilence.isNil.not, {
						ez.value.asFloat.postln;
						parent.txalasilence.synth.set(\comp_thres, ez.value.asFloat);
					});
					~listenparemeters.tempo.comp_thres = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.comp_thres,
				labelWidth: labelwidth
		));


		yindex = yindex + 1;


		// DetectSilence controls //
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = ~txl.do("Tempo detection");

		yindex = yindex + 1;

		guielements.add(\tempothreshold->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("threshold"),// we use mouseUpAction because bug in DetectSilence class. cannot RT update this parameter
				ControlSpec(0.01, 2, \lin, 0.01, 0.2, ""),
				nil,
				initVal: ~listenparemeters.tempo.threshold,
				labelWidth: labelwidth
			).action_({arg ez;
				//[~listenparemeters.tempo.threshold, ez.value.asFloat].postln;
				if (parent.txalasilence.isNil.not, {
					parent.txalasilence.updatethreshold(ez.value.asFloat);
				});
				~listenparemeters.tempo.threshold = ez.value.asFloat;
			});
		);

		yindex = yindex + 1;

		guielements.add(\falltime->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("falltime"),
				ControlSpec(0.01, 3, \lin, 0.01, 0.1, "Ms"),
				{ arg ez;
					if (parent.txalasilence.isNil.not, {
						parent.txalasilence.synth.set(\falltime, ez.value.asFloat);
					});
					~listenparemeters.tempo.falltime = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.falltime,
				labelWidth: labelwidth
		));

		yindex = yindex + 1;


/*
		guielements.add(\checkrate->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("rate"),
				ControlSpec(5, 60, \lin, 1, 30, ""),
				{ arg ez;
					if (parent.txalasilence.isNil.not, {
						parent.txalasilence.synth.set(\checkrate, ez.value.asFloat);
					});
					~listenparemeters.tempo.checkrate = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.checkrate,
				labelWidth: labelwidth
		));

		yindex = yindex + 1.5;
*/

		// Onset pattern detection controls //
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = ~txl.do("Hit onset detection");

		yindex = yindex + 1;

		guielements.add(\onsetthreshold->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("threshold"),
				ControlSpec(0, 1, \lin, 0.01, 0.4, ""),
				{ arg ez;
					if (parent.txalaonset.isNil.not, {
						parent.txalaonset.synth.set(\threshold, ez.value.asFloat);
					});
					~listenparemeters.onset.threshold = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.threshold,
				labelWidth: labelwidth
		));

		 yindex = yindex + 1;

		 guielements.add(\relaxtime->
		 	EZSlider( win,
		 		Rect(20,yloc+(gap*yindex),370,20),
		 		~txl.do("relaxtime"),
		 		ControlSpec(0.0001, 0.5, \lin, 0.0001, 0.05, "ms"),
		 		{ arg ez;
		 			if (parent.txalaonset.isNil.not, {
		 				parent.txalaonset.synth.set(\relaxtime, ez.value.asFloat);
		 			});
		 			~listenparemeters.onset.relaxtime = ez.value.asFloat;
		 		},
		 		initVal: ~listenparemeters.onset.relaxtime,
		 		labelWidth: labelwidth
		 ).round_(0.00001).numberView.maxDecimals_(5) );

		 yindex = yindex + 1.5;
/*
		 guielements.add(\floor->
		 	EZSlider( win,
		 		Rect(20,yloc+(gap*yindex),370,20),
		 		~txl.do("floor"),
		 		ControlSpec(0.001, 5, \lin, 0.001, 0.1, "Ms"),
		 		{ arg ez;
		 			if (parent.txalaonset.isNil.not, {
		 				parent.txalaonset.synth.set(\floor, ez.value.asFloat);
		 			});
		 			~listenparemeters.onset.floor = ez.value.asFloat;
		 		},
		 		initVal: ~listenparemeters.onset.floor,
		 		labelWidth: labelwidth
		 ).round_(0.00001).numberView.maxDecimals_(5) );

		 yindex = yindex + 1;

		 guielements.add(\mingap->
		 	EZSlider( win,
		 		Rect(20,yloc+(gap*yindex),370,20),
		 		~txl.do("mingap"),
		 		ControlSpec(1, 128, \lin, 1, 1, "FFT frames"),
		 		{ arg ez;
		 			if (parent.txalaonset.isNil.not, {
		 				parent.txalaonset.synth.set(\mingap, ez.value.asFloat);
		 			});
		 			~listenparemeters.onset.mingap = ez.value.asFloat;
		 		},
		 		initVal: ~listenparemeters.onset.mingap,
		 		labelWidth: labelwidth
		 ));

yindex = yindex + 1.5;
*/
		guielements.gain.valueAction = ~listenparemeters.gain;
		guielements.tempothreshold.valueAction = ~listenparemeters.tempo.threshold;
		guielements.falltime.valueAction = ~listenparemeters.tempo.falltime;
		guielements.comp_thres.valueAction = ~listenparemeters.tempo.comp_thres;

		//guielements.checkrate.valueAction = ~listenparemeters.tempo.checkrate;
		guielements.onsetthreshold.valueAction = ~listenparemeters.onset.threshold;
		guielements.relaxtime.valueAction = ~listenparemeters.onset.relaxtime;
		//guielements.floor.valueAction = ~listenparemeters.onset.floor;
		//guielements.mingap.valueAction = ~listenparemeters.onset.mingap;


		StaticText(win, Rect(5, yloc+(gap*yindex), 170, 20)).string = ~txl.do("Calibration manager");

		yindex = yindex + 1;

		popup = PopUpMenu(win,Rect(5,yloc+(gap*yindex),170,20))
		.items_( parent.updatepresetfiles("presets_listen") )
		.mouseDownAction_( {arg menu;
			presetslisten = parent.updatepresetfiles("presets_listen");
			menu.items = presetslisten;
		} )
		.action_({ arg menu;
			var data;
			("loading..." + basepath ++ "/presets_listen/" ++ menu.item).postln;
			data = Object.readArchive(basepath ++ "/presets_listen/" ++ menu.item);

			if (data.isNil.not, {

				~hutsunelookup = data[\hutsunelookup];
				~listenparemeters = data[\listenparemeters];

				//if (parent.txalacalibration.isNil.not, {
					/*try {
						guielements.hutsunelookup.valueAction = ~hutsunelookup;
					}{|err|
						"could not set hutsune value".postln;
					} ;*/

					try {
						guielements.gain.valueAction = ~listenparemeters.gain
					}{|err|
						"could not set gain value".postln;
					} ;

					guielements.tempothreshold.value = ~listenparemeters.tempo.threshold;
					guielements.falltime.value = ~listenparemeters.tempo.falltime;
					//guielements.checkrate.value = ~listenparemeters.tempo.checkrate;
					try {
						guielements.comp_thres.value = ~listenparemeters.tempo.comp_thres;
					}{|err|
						"could not set comp_thres value".postln;
					} ;
					guielements.onsetthreshold.value = ~listenparemeters.onset.threshold;
					guielements.relaxtime.value = ~listenparemeters.onset.relaxtime;
					//guielements.floor.value = ~listenparemeters.onset.floor;
					//guielements.mingap.value = ~listenparemeters.onset.mingap;
				//});
			});
		});

		yindex = yindex + 1;

		popup.mouseDown;// force creating the menu list
		try{ // AUTO load first preset
			popup.valueAction_(1);
		}{ |error|
			"no predefined listen preset to be loaded".postln;
			error.postln;
		};

		newpreset = TextField(win, Rect(5, yloc+(gap*yindex), 95, 25));
		Button(win, Rect(105,yloc+(gap*yindex),70,25))
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
			data.put(\listenparemeters, ~listenparemeters);
			data.put(\hutsunelookup, ~hutsunelookup);
			data.writeArchive(basepath ++ "/presets_listen/" ++ filename);

			newpreset.string = ""; //clean field
		});


		Button( win, Rect(190,yloc+(gap*yindex),120,25))
		.states_([
			[~txl.do("lauko"), Color.white, Color.black],
			[~txl.do("lauko"), Color.black, Color.green],
		])
		.action_({ arg but;
			if (but.value.asBoolean, {
				~listenparemeters.tempo.threshold = 0.3;
			},{
				~listenparemeters.tempo.threshold = 0.6;
			});
			guielements.tempothreshold.valueAction = ~listenparemeters.tempo.threshold;
		});


		Button( win, Rect(320,yloc+(gap*yindex),70,25))
		.states_([
			[~txl.do("help"), Color.white, Color.black],
		])
		.action_({ arg but;
			var langst = "", path, file; // eu
			path = basepath[..basepath.findBackwards(Platform.pathSeparator.asString)]; // get rid of last folder
			if (~txl.lang==0, {langst = "_en"});
			if (~txl.lang==1, {langst = "_es"});
			file = path++"documentation/index"++langst++".html";
			[file, path].postln;
			helpwin = WebView().front.url_(file)
		});

		win.front; // Finally
	}
}
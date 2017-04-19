// license GPL
// by www.ixi-audio.net

/*
TxalaCalibration.new()

estimatedControlStep.decimalStrings(precision)

numberbox.minDecimals
g.widgets.do{|x| if(x.isKindOf(EZSlider), {x.round= 0.00001; x.numberView.maxDecimals= 5})}
*/



TxalaCalibration{

	var win, parent, <>guielements;


	*new { |parent|
		^super.new.initTxalaCalibration(parent);
	}

	initTxalaCalibration { |aparent|
		parent = aparent;
		this.doGUI();
	}

	close {
		win.close();
	}

	doGUI {
		var yindex=0, yloc = 10, gap=20, labelwidth=70; //Array.fill(10, {nil});
		win = Window(~txl.do("Input calibration"),  Rect(10, 50, 400, 230));
		win.onClose = {
			parent.txalacalibration = nil
/*			if (txalasilence.isNil.not, {txalasilence.kill()});
			if (txalaonset.isNil.not, {txalaonset.kill()});
			if (~txalascore.isNil.not, {~txalascore.close});
			scopesynth.free;
			scope.free;*/
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
			).sliderView.mouseUpAction_({arg ez;
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


		guielements.add(\comp_thres->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("comp_thres"),
				ControlSpec(0.01, 1, \lin, 0.01, 0.2, "RMS"),
				{ arg ez;
					if (parent.txalasilence.isNil.not, {
						parent.txalasilence.synth.set(\comp_thres, ez.value.asFloat);
					});
					~listenparemeters.tempo.comp_thres = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.comp_thres,
				labelWidth: labelwidth
		));

		/*
		yindex = yindex + 1;

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
		));*/

		yindex = yindex + 1.5;


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

		// yindex = yindex + 1;
		//
		// guielements.add(\floor->
		// 	EZSlider( win,
		// 		Rect(20,yloc+(gap*yindex),370,20),
		// 		~txl.do("floor"),
		// 		ControlSpec(0.001, 5, \lin, 0.001, 0.1, "Ms"),
		// 		{ arg ez;
		// 			if (parent.txalaonset.isNil.not, {
		// 				parent.txalaonset.synth.set(\floor, ez.value.asFloat);
		// 			});
		// 			~listenparemeters.onset.floor = ez.value.asFloat;
		// 		},
		// 		initVal: ~listenparemeters.onset.floor,
		// 		labelWidth: labelwidth
		// ).round_(0.00001).numberView.maxDecimals_(5) );
		//
		// yindex = yindex + 1;
		//
		// guielements.add(\mingap->
		// 	EZSlider( win,
		// 		Rect(20,yloc+(gap*yindex),370,20),
		// 		~txl.do("mingap"),
		// 		ControlSpec(1, 128, \lin, 1, 1, "FFT frames"),
		// 		{ arg ez;
		// 			if (parent.txalaonset.isNil.not, {
		// 				parent.txalaonset.synth.set(\mingap, ez.value.asFloat);
		// 			});
		// 			~listenparemeters.onset.mingap = ez.value.asFloat;
		// 		},
		// 		initVal: ~listenparemeters.onset.mingap,
		// 		labelWidth: labelwidth
		// ));

yindex = yindex + 1.5;

			// hutsune timeout control
		//StaticText(win, Rect(5, yloc+(gap*yindex), 380, 25)).string = ~txl.do("Hutsune detection timeout");

		// yindex = yindex + 1;

		/*guielements.add(\hutsunelookup ->
			EZSlider( win,
				Rect(20,yloc+(gap*yindex),370,20),
				~txl.do("lookup"),
				ControlSpec(0, 1, \lin, 0.01, 1, ""),
				{ arg ez;
					~hutsunelookup = ez.value.asFloat;
				},
				initVal: ~hutsunelookup,
				labelWidth: labelwidth
		));*/

		guielements.add(\hutsunelookup ->
			Button(win, Rect(20,yloc+(gap*yindex),170,30))
			.states_([
				[~txl.do("Hutsune detection timeout"), Color.white, Color.black],
				[~txl.do("Hutsune detection timeout"), Color.black, Color.green],
			])
			.action_({ arg butt;
				~hutsunelookup = butt.value;
			})
		);


		try {
			guielements.hutsunelookup.valueAction = ~hutsunelookup;
		}{|err|
			"could not set hutsune button value".postln;
		} ;
		guielements.gain.valueAction = ~listenparemeters.gain;
		guielements.tempothreshold.valueAction = ~listenparemeters.tempo.threshold;
		guielements.falltime.valueAction = ~listenparemeters.tempo.falltime;
		guielements.comp_thres.valueAction = ~listenparemeters.tempo.comp_thres;

		//guielements.checkrate.valueAction = ~listenparemeters.tempo.checkrate;
		guielements.onsetthreshold.valueAction = ~listenparemeters.onset.threshold;
		guielements.relaxtime.valueAction = ~listenparemeters.onset.relaxtime;
		//guielements.floor.valueAction = ~listenparemeters.onset.floor;
		//guielements.mingap.valueAction = ~listenparemeters.onset.mingap;

		win.front;
	}
}
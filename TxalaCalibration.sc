// license GPL
// by www.ixi-audio.net

/*
TxalaCalibration.new()
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
		var yindex=0, yloc = 10, gap=20; //Array.fill(10, {nil});
		win = Window("Input calibration",  Rect(10, 50, 360, 300));
		win.onClose = {
			parent.txalacalibration = nil
/*			if (txalasilence.isNil.not, {txalasilence.kill()});
			if (txalaonset.isNil.not, {txalaonset.kill()});
			if (~txalascore.isNil.not, {~txalascore.close});
			scopesynth.free;
			scope.free;*/
		};

		guielements = ();

		// calibration //////////////////////////////////////////////

		// ~gain
		guielements.add(\gain-> EZSlider( win,
			Rect(0,yloc+(gap*yindex),350,20),
			"gain in",
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
			labelWidth: 60;
		));

		yindex = yindex + 1;

		// DetectSilence controls //
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Tempo detection";

		yindex = yindex + 1;

		guielements.add(\tempothreshold->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"threshold",// we use mouseUpAction because bug in DetectSilence class. cannot RT update this parameter
				ControlSpec(0.01, 2, \lin, 0.01, 0.2, ""),
				nil,
				initVal: ~listenparemeters.tempo.threshold,
				labelWidth: 60;
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
				Rect(0,yloc+(gap*yindex),350,20),
				"falltime",
				ControlSpec(0.01, 3, \lin, 0.01, 0.1, "Ms"),
				{ arg ez;
					if (parent.txalasilence.isNil.not, {
						parent.txalasilence.synth.set(\falltime, ez.value.asFloat);
					});
					~listenparemeters.tempo.falltime = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.falltime,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\checkrate->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"rate",
				ControlSpec(5, 60, \lin, 1, 30, ""),
				{ arg ez;
					if (parent.txalasilence.isNil.not, {
						parent.txalasilence.synth.set(\checkrate, ez.value.asFloat);
					});
					~listenparemeters.tempo.checkrate = ez.value.asFloat;
				},
				initVal: ~listenparemeters.tempo.checkrate,
				labelWidth: 60;
		));

		yindex = yindex + 1.5;

		// hutsune timeout control
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Hutsune detection timeout";

		yindex = yindex + 1;

		guielements.add(\hutsunelookup ->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"lookup",
				ControlSpec(0, 1, \lin, 0.01, 1, ""),
				{ arg ez;
					~hutsunelookup = ez.value.asFloat;
				},
				initVal: ~hutsunelookup,
				labelWidth: 60;
		));

		yindex = yindex + 1.5;

		// Onset pattern detection controls //
		StaticText(win, Rect(5, yloc+(gap*yindex), 180, 25)).string = "Hit onset detection";

		yindex = yindex + 1;

		guielements.add(\onsetthreshold->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"threshold",
				ControlSpec(0, 1, \lin, 0.01, 0.4, ""),
				{ arg ez;
					if (parent.txalaonset.isNil.not, {
						parent.txalaonset.synth.set(\threshold, ez.value.asFloat);
					});
					~listenparemeters.onset.threshold = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.threshold,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\relaxtime->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"relaxtime",
				ControlSpec(0.001, 0.5, \lin, 0.001, 0.05, "ms"),
				{ arg ez;
					if (parent.txalaonset.isNil.not, {
						parent.txalaonset.synth.set(\relaxtime, ez.value.asFloat);
					});
					~listenparemeters.onset.relaxtime = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.relaxtime,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\floor->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"floor",
				ControlSpec(0.01, 10, \lin, 0.01, 0.1, "Ms"),
				{ arg ez;
					if (parent.txalaonset.isNil.not, {
						parent.txalaonset.synth.set(\floor, ez.value.asFloat);
					});
					~listenparemeters.onset.floor = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.floor,
				labelWidth: 60;
		));

		yindex = yindex + 1;

		guielements.add(\mingap->
			EZSlider( win,
				Rect(0,yloc+(gap*yindex),350,20),
				"mingap",
				ControlSpec(1, 128, \lin, 1, 1, "FFT frames"),
				{ arg ez;
					if (parent.txalaonset.isNil.not, {
						parent.txalaonset.synth.set(\mingap, ez.value.asFloat);
					});
					~listenparemeters.onset.mingap = ez.value.asFloat;
				},
				initVal: ~listenparemeters.onset.mingap,
				labelWidth: 60;
		));

		win.front;
	}
}
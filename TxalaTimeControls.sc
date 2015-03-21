// license GPL
// by www.ixi-audio.net

/* TxalaTimeControls
*/



TxalaTimeControls {

	var win, buttonxloc, <sliders, ampbut, xloc, yloc, width, gap, currentpath;

	*new { | win, xp=5 yp=5, ws=400, gap=25, path |
		^super.new.initTxalaTimeControls(win, xp, yp, ws, gap, path);
	}

	initTxalaTimeControls { |awin, ax, ay, aw, agap, apath|
		win = awin;
		xloc = ax;
		yloc = ay;
		width = aw;
		gap = agap;
		currentpath = apath;
		buttonxloc = xloc + width + 20;
		sliders = Array.fill(4, {[nil,nil]}); // slider and its autopilot button associated

		// tempo //
		sliders[0][0] = EZSlider( win,
			Rect(xloc,yloc,width,20),
			"tempo",
			ControlSpec(20, 250, \lin, 1, ~tempo, "BPMs"),
			{ arg ez;
				~tempo = ez.value;
			},
			initVal: ~tempo,
			labelWidth: 80;
		);

		Button(win, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~tempo", ControlSpec(20, 250), sliders[0][0], presetspath:currentpath);
		});

		// tempo swing //
		yloc = yloc+gap;
		sliders[1][0] = EZSlider( win,
			Rect(xloc,yloc,width,20),
			"tempo swing",
			ControlSpec(0.01, 1, \lin, 0.01, ~swing, "ms"),
			{ arg ez;
				~swing = ez.value;
			},
			initVal: ~swing,
			labelWidth: 80;
		);

		Button(win, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~swing", ControlSpec(0.001, 0.2), sliders[1][0]);
		});

		// gap //
		yloc = yloc+gap;
		sliders[2][0] = EZSlider( win,
			Rect(xloc,yloc,width,20),
			"gap",
			ControlSpec(0.001, 1, \lin, 0.001, ~gap, "ms"),
			{ arg ez;
				~gap = ez.value;
			},
			initVal: ~gap,
			labelWidth: 80;
		);

		Button(win, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~gap", ControlSpec(0.001, 1), sliders[2][0]);
		});

		// gap swing //
		yloc = yloc+gap;
		sliders[3][0] = EZSlider( win,
			Rect(xloc,yloc,width,20),
			"gap swing",
			ControlSpec(0.001, 1, \lin, 0.001, ~gapswing, "ms"),
			{ arg ez;
				~gapswing = ez.value;
			},
			initVal: ~gapswing,
			labelWidth: 80;
		);

		Button(win, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~gapswing", ControlSpec(0.001, 1), sliders[3][0]);
		});

		// amplitude does not go with autopilot and therefore is stored in its own var
		yloc = yloc+gap;
		ampbut = EZSlider( win,
			Rect(xloc,yloc,width,20),
			"amp",
			ControlSpec(0, 1, \lin, 0.01, ~amp, "ms"), //\amp,
			{ arg ez;
				~amp = ez.value;
			},
			initVal: ~amp,
			labelWidth: 80;
		);
		Button(win, Rect(buttonxloc-20,yloc,20,20))
		.states_([
			["P", Color.white, Color.black],
		])
		.action_({ arg butt;
			ParamWin.new("~amp", ControlSpec(0, 1), ampbut);
		});
	}

	updatesliders {
		sliders[0][0].value = ~tempo;
		sliders[1][0].value = ~swing;
		sliders[2][0].value = ~gap;
		sliders[3][0].value = ~gapswing;
		ampbut.value = ~amp;
	}
}
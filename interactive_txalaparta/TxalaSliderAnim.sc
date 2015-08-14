// license GPL
// by www.ixi-audio.net

/*
makilaanims = TxalaSliderAnim.new( 10 , 10);
~makilaanims.makilaF(makilaindex, 0.2);//slider animation. makila index is 0 or 1 means which of both makilas beats first. //I THINK THIS CAN BE REMOVED//
*/



TxalaSliderAnim {

	var win, xloc, yloc, width, gap, makilasliders;//, <>drawingSetBuffer, rot, drawFunc;

	*new { | win, x=10 y=10, w=270, h=550, gap=10 |
		^super.new.initTxalaSliderAnim(win, x, y, w, h, gap);
	}

	initTxalaSliderAnim { |awin, ax, ay, aw, ah, agap|
		var ind = 0, thegap = 0;

		win = awin;
		xloc = ax;
		yloc = ay;
		width = aw;
		gap = agap;

		makilasliders = [nil, nil]; // single player

		makilasliders.do({arg item, i;
			makilasliders[i] = Slider(win, Rect(xloc+(37*ind), yloc, 35, 180));
			makilasliders[i].orientation = \vertical;
			makilasliders[i].thumbSize = 130;
			makilasliders[i].value = 1;
			ind = ind + 1;
		});

	}

	makilaF {arg makilaindex, time;
		var steps, stepvalue, gap=0.05, loopF, sl;

		sl = makilasliders.wrapAt(makilaindex);
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
		});

		AppClock.play(loopF);
	}
}

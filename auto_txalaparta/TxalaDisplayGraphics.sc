// license GPL
// by www.ixi-audio.net

/*
TxalaDisplayGraphics.new( 10 , 10)
*/



TxalaDisplayGraphics {

	var win, xloc, yloc, width, gap, makilasliders, anim;//, <>drawingSetBuffer, rot, drawFunc;

	*new { | x=10 y=10, w=270, h=550, gap=10 |
		^super.new.initTxalaDisplayGraphics(x, y, w, h, gap);
	}

	initTxalaDisplayGraphics { |ax, ay, aw, ah, agap|
		var ind = 0, thegap = 0;

		win = Window("Txalaparta", Rect(ax,ay,aw,ah));
		win.onClose = {
			~makilaanims = nil;
		};

		xloc = ax;
		yloc = ay;
		width = aw;
		gap = agap;

		makilasliders = [[nil, nil], [nil, nil]]; // two for each player

		makilasliders.do({arg list;
			list.do({arg item, i;
				list[i] = Slider(win, Rect(10+thegap+(61*ind), yloc, 60, 350));
				list[i].orientation = \vertical;
				list[i].thumbSize = 240;
				list[i].value = 1;
				ind = ind + 1;
			});
			thegap = gap;
		});

		anim = TxalaCircleAnim.new(win, 135, 460);

		win.front;
	}

	close {
		win.close();
	}

	scheduleDraw {arg data;
		anim.scheduleDraw(data)
	}

	makilaF {arg txakunflag, makilaindex, time;
		var steps, stepvalue, gap=0.05, loopF, sl;

		sl = makilasliders[txakunflag].wrapAt(makilaindex);
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

// license GPL
// by www.ixi-audio.net

/*
TxalaDisplayGraphics.new(10, 10, 270, 660, 10);
*/



TxalaDisplayGraphics {

	var win, xloc, yloc, width, gap, makilasliders, output, enabledButs, <>drawingSetBuffer, rot, drawFunc;

	*new { | ax=300 ay=190, aw=400, ah=500, agap=35 |
		^super.new.initTxalaDisplayGraphics(ax, ay, aw, ah, agap);
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
		enabledButs = [nil, nil]; // txakun and errena
		rot = 0;

		if (~enabled.isNil, {~enabled = [true, true]});

		drawingSetBuffer = [Array.fill(4, {[0, 0, false, 10]}), Array.fill(4, {[0, 0, false, 10]})]; //one buelta with a max 4 hits each part

		makilasliders.do({arg list;
			list.do({arg item, i;
				list[i] = Slider(win, Rect(xloc+thegap+(61*ind), yloc, 60, 350));
				list[i].orientation = \vertical;
				list[i].thumbSize = 240;
				list[i].value = 1;
				ind = ind + 1;
			});
			thegap = gap;
		});

		// TXAKUN
		enabledButs[0] = Button(win, Rect(xloc,yloc+350,120,50))
		.states_([
			["txakun", Color.white, Color.black],
			["txakun", Color.black, Color.red],
		])
		.action_({ arg butt;
			~enabled[0] = butt.value.asBoolean;
		})
		.valueAction_(1);

		// ERRENA
		enabledButs[1] = Button(win, Rect(xloc+130,yloc+350,120,50))
		.states_([
			["errena", Color.white, Color.black],
			["errena", Color.black, Color.blue],
		])
		.action_({ arg butt;
			~enabled[1] = butt.value.asBoolean;
		})
		.valueAction_(1);

		output = StaticText(win, Rect(xloc, yloc+400, 200, 20));


		drawFunc = { // drawing the visualization of circles
			var dur, dpt; // duration of the circle and degrees per time unit
			dur = 60/~tempo; // duration of the cycle in secs
			dpt = 360/dur; // how many degrees each ms

			Pen.translate(135, 520); // location of the circle
			Pen.color = Color.black;
			Pen.addArc(0@0, 80, 0, 360);
			Pen.line(10@90.neg, 15@87.neg); // > mark
			Pen.line(15@87.neg, 10@84.neg);

			Pen.perform(\stroke); // static if maintaining pulse

			if (~pulse.not, {

				try {
					rot = rot + (((this.drawingSetBuffer[1][0][0]*dpt)))*(pi/180); // apply the rotation of the current beat
				} {|error| rot = 0};
				Pen.rotate( rot )
			});
			Pen.line(0@90.neg, 0@90); //vertical line
			Pen.perform(\stroke); // static if maintaining pulse

			//drawHitSet.value(drawingSetBuffer[0], dur, dpt);

			//Pen.rotate( rot );

			//this.drawHitSet(this.drawingSetBuffer[1], dur, dpt);
			this.drawingSetBuffer[1].do({arg data; // --> [msecs, txakunflag, amp]
				var offset;

				if (data[3] <= 1, { // only the ones with a valid data
					if (data[2], {//txakun
						offset = 270;
						Pen.color = Color.red; //.alpha_(0.8).set;
						},{
							offset = 90;
							Pen.color = Color.blue;
					}); // txakun up, errena down

					Pen.use{
						Pen.rotate( (((data[1]*dpt)-offset)*(pi/180)) );
						Pen.addArc((80)@(0), data[3]*12, 0, 360); // circles representing beats
						Pen.perform(\fill);
					};
				});
			});

			this.drawingSetBuffer = [ this.drawingSetBuffer[1], Array.fill(4, {[0, -1, false, 10]}) ];

		};

		win.drawFunc = drawFunc;
		win.front;
	}

	close {
		win.close();
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
			//win.refresh;
		});

		AppClock.play(loopF);
	}

	scheduleDraw {arg data;
		drawingSetBuffer[1] = data; // store in second slot
		win.refresh;
	}

}

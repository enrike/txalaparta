// license GPL
// by www.ixi-audio.net

/*
TxalaCircleAnim.new();
*/



TxalaCircleAnim {

	var parent, xloc, yloc, width, <>drawingSetBuffer, rot, drawFunc;

	*new { | parent, ax=300 ay=10, aw=180, ah=190 |
		^super.new.initTxalaCircleAnim(parent, ax, ay, aw, ah);
	}

	initTxalaCircleAnim { |aparent, ax, ay, aw, ah|
		var ind = 0, thegap = 0;

		parent = aparent;
		xloc = ax;
		yloc = ay;
		width = aw;

		rot = 0;

		drawingSetBuffer = [Array.fill(4, {[0, 0, false, 10]}), Array.fill(4, {[0, 0, false, 10]})]; //one buelta with a max 4 hits each part

		if (~tempo.isNil, {~tempo=120});
		if (~pulse.isNil, {~pulse=true});


		drawFunc = { // drawing the visualization of circles
			var dur, dpt; // duration of the circle and degrees per time unit
			dur = 60/~tempo; // duration of the cycle in secs
			dpt = 360/dur; // how many degrees each ms

			Pen.translate(xloc, yloc); //** location of the circle **//
			Pen.color = Color.black;
			Pen.addArc(0@0, 80, 0, 360);
			Pen.line(10@90.neg, 15@87.neg); // > mark
			Pen.line(15@87.neg, 10@84.neg);

			Pen.perform(\stroke); // static if maintaining pulse

			if (~pulse.not, {

				try {
					rot = rot + ((((drawingSetBuffer[1][0][0]*dpt)))*(pi/180)); // apply the rotation of the current beat
				} {|error| rot = 0};
				Pen.rotate( rot )
			});
			Pen.line(0@90.neg, 0@90); //vertical line
			Pen.perform(\stroke); // static if maintaining pulse

			//drawHitSet.value(drawingSetBuffer[0], dur, dpt);

			//Pen.rotate( rot );

			//this.drawHitSet(this.drawingSetBuffer[1], dur, dpt);
			drawingSetBuffer[1].do({arg data; // --> [msecs, txakunflag, amp]
				var offset;

				if (data[3] <= 2, { // only the ones with a valid data
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

			["drawing set buffer", drawingSetBuffer].postln;
			drawingSetBuffer = [ drawingSetBuffer[1], Array.fill(4, {[0, -1, false, 10]}) ]; // clear

		};

		parent.drawFunc = drawFunc;
	}

	scheduleDraw {arg data;
		drawingSetBuffer[1] = data; // store in second slot
		parent.refresh;
	}
}

// license GPL
// by www.ixi-audio.net

/*
TxalaCircleAnim.new();
*/



TxalaCircle {

	var parent, xloc, yloc, width, <>drawingSetBuffer, rot, drawFunc;

	*new { | parent, ax=300 ay=10, aw=180, ah=190 |
		^super.new.initTxalaCircle(parent, ax, ay, aw, ah);
	}

	initTxalaCircle { |aparent, ax, ay, aw, ah|
		var ind = 0, thegap = 0;

		parent = aparent;
		xloc = ax;
		yloc = ay;
		width = aw;
		rot = 0;

		//drawingSetBuffer = [Array.fill(4, {[0, 0, false, 10]}), Array.fill(4, {[0, 0, false, 10]})]; //one buelta with a max 4 hits each part
		drawingSetBuffer = [[], []];

		if (~tempo.isNil, {~tempo=120});
		if (~pulse.isNil, {~pulse=true});


		drawFunc = { // drawing the visualization of circles
			var dur, dpt; // duration of the circle and degrees per time unit
			dur = 120/~tempo; // duration of the cycle in secs. there is something wrong with this
			dpt = 360/dur; // how many degrees each ms

			Pen.translate(xloc, yloc); //** location of the circle **//
			Pen.color = Color.black;
			Pen.addArc(0@0, 80, 0, 360);
			Pen.line(10@90.neg, 15@87.neg); // > mark
			Pen.line(15@87.neg, 10@84.neg);

			Pen.perform(\stroke); // static if maintaining pulse

			/*			if (~pulse.not, {

			try {
			rot = rot + ((((drawingSetBuffer[1][0][0]*dpt)))*(pi/180)); // apply the rotation of the current beat
			} {|error| rot = 0};
			Pen.rotate( rot )
			});*/
			Pen.line(0@90.neg, 0@90); //vertical line
			Pen.perform(\stroke); // static if maintaining pulse

			(drawingSetBuffer[0]++drawingSetBuffer[1]).takeThese({ arg item; item[3]==10 }).do({arg data;
				var offset;
				//data.postln;
				if (data[2], {//txakun
					offset = 270;
					Pen.color = Color.red;
				},{
					offset = 90;
					Pen.color = Color.blue;
				}); // txakun up, errena down

				Pen.use{
					Pen.rotate( (((data[1]*dpt)-offset)*(pi/180)) );
					Pen.addArc((80)@(0), data[3]*20, 0, 360); // circles representing beats
					Pen.perform(\fill);
				}
			});
			//"-----------------".postln;
		};

		parent.drawFunc = drawFunc; // parent is a window that can take a refresh
	}

	scheduleDraw {arg data, pos;
		["pos", pos].postln;
		drawingSetBuffer[pos] = data; // store in its slot
		parent.refresh;
	}
}

// license GPL
// by www.ixi-audio.net

/*
~outputwin = OutputWin.new();
~outputwin.msg( "whatever", Color.green);

p = Routine({
	inf.do({arg counter;
		~outputwin.msg( "helloooo..." + counter, Color.rand);
		0.1.wait;
	});
});

AppClock.play(p);
*/

OutputWin {

	var win, outfield, buffersize=5000;

	*new {
		^super.new.initOutputWin();
	}

	initOutputWin {
		win = Window("Output View",  Rect(960, 22, 400, 745));

		win.onClose = {
			if (~outputwin.isNil.not, {~outputwin = nil});
		};

		outfield = TextView(win, Rect(0, 0, 400, 745));
		outfield.font = Font("Verdana", 11);
		outfield.editable = false;
		outfield.backColor = Color.white;

		win.alwaysOnTop = true;

		win.front;
	}

	msg { arg st, col=Color.black;
		{
			if ( (outfield.string.size > buffersize), {outfield.string = outfield.string[(outfield.string.size-buffersize)..]}); // do not grow too long
			outfield.setString( (st+"\n"), outfield.string.size); // append
			//outfield.setStringColor(col, outfield.string.size-st.size-2, outfield.string.size); // NOT WORKING RIGHT when buffer grows too big and we need to start chopping
			outfield.select(outfield.string.size, 1); // autoscroll
	    }.defer
	}

	close {
		//"closing down".postln;
		win.close;
		~outputwin = nil;
	}

}
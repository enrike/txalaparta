// license GPL
// by www.ixi-audio.net

/*
~outputwin = OutputWin.new();
~outputwin.post( "whatever", Color.green);

p = Routine({
	inf.do({arg counter;
		~outputwin.post( "helloooo..." + counter);
		0.1.wait;
	});
});

AppClock.play(p);
*/


OutputWin {

	var win, outfield;

	*new {
		^super.new.initOutputWin();
	}

	initOutputWin {
		win = Window("Output View",  Rect(10, 50, 400, 730));

		win.onClose = {
			if (~outputwin.isNil.not, {~outputwin = nil});
		};

		outfield = TextView(win, Rect(0, 0, 400, 730));
		outfield.font = Font("Verdana", 12);
		outfield.editable = false;
		outfield.backColor = Color.white;

		win.alwaysOnTop = true;

		win.front;
	}

	post { arg st, col=Color.black;
		{
			outfield.setString( (st+"\n"), outfield.string.size);
			outfield.setStringColor(col, 0, st.size);
			outfield.select(outfield.string.size, 1);
	    }.defer
	}

	close {
		"closing down".postln;
		win.close;
		~outputwin = nil;
	}

}
/*
digital txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta

to do:
- prevent global name collision between both apps (~buffers)
- quit all windows and free synths on main window exit
*/

(
var dir = thisProcess.nowExecutingPath.dirname;
w = Window.new ("Digital Txalaparta", Rect(100, 100 , 300, 200));


Button(w, Rect(10,50,200,25))
.states_([
	["Interactive Txalaparta", Color.white, Color.black],
])
.action_({ arg butt;
	s.boot;
	s.waitForBoot {
		TxalaInteractive.new(s, dir++"/interactive_txalaparta");
	};
});

Button(w, Rect(10,75,200,25))
.states_([
	["AutoTxalaparta", Color.white, Color.black],
])
.action_({ arg butt;
	s.boot;
	s.waitForBoot {
		TxalaAuto.new(s, dir++"/auto_txalaparta");
	};
});


Button(w, Rect(10,100,200,25))
.states_([
	["About", Color.white, Color.grey],
])
.action_({ arg butt;
	"http://www.ixi-audio.net/txalaparta".postln;
});



		w.onClose = {
/*			if (txalatempo.isNil.not, {txalatempo.closeGUI()});
			if (~makilaanims.isNil.not, {~makilaanims.close()});
			if (~txalascore.isNil.not, {~txalascore.close});*/
			AppClock.clear;
			SystemClock.clear;

		};

w.front;
)
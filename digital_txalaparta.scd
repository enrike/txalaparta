/*
digital txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta

to do:
- create tutorials

ATX
- auto prefs?
- implement markov chains and feed with data from ITX?

ITX
- find a better way to deal with the hutsune. disabling answer prority would solve this?
- non sync compasses time division control implement. can this be automatic somehow?
- test Markov chains
- double hutsunes! find out why does this happen
- timeline does not update number of planks when changing txalaset and chorma. need to close and open the timeline to update.
- sometimes many instances of the synths or the OSC responders stay in memory duplicating the detected events in the txalaparta input, this is very random.
- memory manager for licks (separated from MC memory)
- save buttons to open saveas dialogue? (no need to add then the field next to it)
*/

(
var dir = thisProcess.nowExecutingPath.dirname, ti, ta, lang;

dir.postln;

lang = Object.readArchive(dir ++ "/" ++ "prefs.preset")[\lang];
~txl = TxalaLang.new(lang); // 0 eng, 1 es, 2 eu

/////

w = Window.new (~txl.do("Digital Txalaparta"), Rect(100, 100 , 300, 200));

Button(w, Rect(10,50,200,25))
.states_([
	[~txl.do("Interactive Txalaparta"), Color.white, Color.black],
])
.action_({ arg butt;
	s.boot;
	s.waitForBoot {
		ti = TxalaInteractive.new(s, dir++"/interactive_txalaparta");
	};
});

Button(w, Rect(10,75,200,25))
.states_([
	["AutoTxalaparta", Color.white, Color.black],
])
.action_({ arg butt;
	s.boot;
	s.waitForBoot {
		ta = TxalaAuto.new(s, dir++"/auto_txalaparta");
	};
});


Button(w, Rect(10,100,200,25))
.states_([
	[~txl.do("About"), Color.white, Color.grey],
])
.action_({ arg butt;
	"http://www.ixi-audio.net/txalaparta".postln;
});

PopUpMenu(w,Rect(10,130, 90,20))
.items_(["English","Castellano", "Euskera"])
.action_({ arg menu;
	~txl.lang = menu.value;
}).valueAction_(lang);

w.onClose = {
	var data, filename = "prefs.preset";
	data = Dictionary.new;
	data.put(\lang, ~txl.lang);
	data.writeArchive(dir ++ "/" ++ filename);

	if (ti.isNil.not, {ti.kill});
	if (ta.isNil.not, {ta.kill});
	AppClock.clear;
	SystemClock.clear;
	s.quit;
	if (dir.find("xalaparta").isNil.not, {0.exit}); //quits the lang when standalone
};
w.userCanClose = False;
s.boot;
w.front;
)
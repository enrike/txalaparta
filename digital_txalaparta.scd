/*
digital txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

What is the txalaparta?
https://en.wikipedia.org/wiki/Txalaparta

Txalaparta video tutorial
https://www.youtube.com/playlist?list=PLauBVgFtMcLDCSNNLCngm0BtBas2SsFB7
*/

(
var dir = thisProcess.nowExecutingPath.dirname, ti, ta, lang, helpwin;

lang = Object.readArchive(dir ++ "/" ++ "prefs.preset")[\lang];
~txl = TxalaLang.new(lang); // 0 eng, 1 es, 2 eu

o = Server.local.options;
o.numOutputBusChannels = 2;
o.numInputBusChannels = 2;

/////

w = Window.new (~txl.do("Digital Txalaparta"), Rect(100, 100 , 300, 170));

Button(w, Rect(10,10,280,50))
.states_([

	[~txl.do("Interactive Txalaparta"), Color.white, Color.black],
])
.action_({ arg butt;
	s.boot;
	s.waitForBoot {
		ti = TxalaInteractive.new(s, dir++"/interactive_txalaparta");
	};
});

Button(w, Rect(10,60,280,50))
.states_([
	["AutoTxalaparta", Color.white, Color.black],
])
.action_({ arg butt;
	s.boot;
	s.waitForBoot {
		ta = TxalaAuto.new(s, dir++"/auto_txalaparta");
	};
});



PopUpMenu(w,Rect(10,120, 90,20))
.items_(["English","Castellano", "Euskera"])
.action_({ arg menu;
	~txl.lang = menu.value;
}).valueAction_(lang);

w.onClose = {
	var data, filename = "prefs.preset";
	data = Dictionary.new;
	data.put(\lang, ~txl.lang);
	data.writeArchive(dir ++ "/" ++ filename);

	if (helpwin.isNil.not, {helpwin.close});
	if (ti.isNil.not, {ti.kill});
	if (ta.isNil.not, {ta.kill});
	AppClock.clear;
	SystemClock.clear;
	s.quit;
	if (dir.find("collider").isNil, {0.exit}); //should quit the lang ONLY when standalone
};



Button(w, Rect(140,140, 150,25))
.states_([
	[~txl.do("About / Help"), Color.white, Color.grey],
])
.action_({ arg butt;
	var langst = "", file, path; // eu
	if (~txl.lang==0, {langst = "_en"});
	if (~txl.lang==1, {langst = "_es"});

		Platform.case(
			\linux,   { path = dir },
			\osx,     { path = "pwd".unixCmdGetStdOut;
			               path = path[..path.size-2];// get rid char return
			               path = path +/+ "SCClassLibrary" +/+"txalaparta-master";
		              },
			\windows, {
			            path = "ECHO %CD%".unixCmdGetStdOut;
		                path = path[..path.size-2];// get rid of char return
		               }
	);
	//path.postln;
	file = path +/+ "documentation" +/+ "index" ++langst++ ".html";

	//file = dir++"/documentation/index"++langst++".html";

	helpwin = WebView().front.url_(file)
});


w.userCanClose = False;
s.boot;
w.front;
)
/*
interactive txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta
*/


/*Platform.case(
	//\osx,       { "OSX".postln },
	\linux,     { Server.default = Server.internal },
	\windows,   { Server.default = Server.internal }
);*/


(

s.boot;
s.waitForBoot {
	TxalaInteractive.new(s, thisProcess.nowExecutingPath.dirname);
};

)


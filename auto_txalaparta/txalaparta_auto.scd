/*
automatic txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta
*/


// this is to avoid problem in some windows machines
/*Platform.case(
	//\osx,       { "OSX".postln },
	\linux,     { Server.default = Server.internal },
	\windows,   { Server.default = Server.internal }
);*/

(
s.boot;
s.waitForBoot {
	TxalaAuto.new(s, thisProcess.nowExecutingPath.dirname);
};
)

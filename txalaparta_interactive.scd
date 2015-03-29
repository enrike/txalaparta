/*
interactive txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta

s.meter
s.makeGui
s.scope(2)
s.freqscope

s.prepareForRecord (path)
s.record(path)
s.stopRecording

s.volume = -20 //-90 <> 5.5
s.mute
s.unmute
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


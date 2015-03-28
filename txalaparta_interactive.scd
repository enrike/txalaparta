(

/*Platform.case(
	//\osx,       { "OSX".postln },
	\linux,     { Server.default = Server.internal },
	\windows,   { Server.default = Server.internal }
);*/

s.boot;
s.waitForBoot {
	TxalaInteractive.new(s, thisProcess.nowExecutingPath.dirname);
};

)


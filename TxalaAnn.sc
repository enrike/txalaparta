// license GPL
// by www.ixi-audio.net

/* just wraps NeuralNet
m = TxalaAnn.new
m = next(3);
*/



TxalaAnn {

	var lasthit, dimension, ann;

	*new { | dimension = 5 |
		^super.new.initAnn(dimension);
	}

	initAnn { |adimension|
		dimension = adimension; // number or output values: 0,1,2,3,4
		this.reset();
	}

	reset {
		lasthit = 2;
		try {
			ann = NeuralNet(dimension, 2, dimension);
		}{|error|
			"no NeuralNet class?".postln;
		};
	}


	next { arg detected;
		var curhits;

		[detected, lasthit].postln;

		ann.train1( [0,0,0,0,0].put(lasthit,1), [0,0,0,0,0].put(detected,1) ); //prev, now
		curhits = ann.calculate( [0,0,0,0,0].put(detected,1) ).maxIndex;

		^curhits;
	}
}
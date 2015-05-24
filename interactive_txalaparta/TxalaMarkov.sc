// license GPL
// by www.ixi-audio.net

/* 1st order markov chain with tipical txalaparta rhythm values. defaults to 0,1,2,3,4 hits
m = TxalaMarkov2.new
m.next(3) //
m.reset
*/



TxalaMarkov{
	var <>beatdata;
	var mem, options, dimension, >update=true;

	*new { | dimension = 5 |
		^super.new.initTxalaMarkov(dimension);
	}

	initTxalaMarkov { |adimension|
		dimension = adimension; // number or output values: 0,1,2,3,4
		this.reset();
	}

	reset {
		"reseting Markov Chain states".postln;
		mem = nil; // me last output <--
		options = Array.fill(dimension, {arg n=0; n}); // [0,1,2,3,4]
		beatdata = [
			[1,0,0,0,0],
			[0,1,0,0,0],
			[0,0,1,0,0],
			[0,0,0,1,0],
			[0,0,0,0,1]
		];
	}

	loaddata{ arg data;
		if (data.rank == beatdata.rank, {
			this.reset();
			beatdata = data;
		}, {
			"cannot load memory file beause it does not match current answer system".postln;
		})
	}

	next{arg input;
		var output=0, weights;
		if (mem.isNil, { mem = [input] }); // first time
		if (~learning, {beatdata[mem[0]][input] = beatdata[mem[0]][input] + 1});
		weights = beatdata[input];
		output = options.wchoose(weights.normalizeSum);
		if (output == 0 && input == 0, {
			output= 2;
		});
		mem = [output];//[num]++mem[0..mem.size-2]; // push right
		^output
	}
}
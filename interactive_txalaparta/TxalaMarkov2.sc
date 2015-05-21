// license GPL
// by www.ixi-audio.net

/* markov chain with tipical txalaparta rhythm values. defaults to 0,1,2,3,4 hits
m = TxalaMarkov2.new
m.next(0) //
m.reset
*/



TxalaMarkov2{
	var <>beatdata;
	var mem, options, dimension, >update=true;

	*new { | dimension = 5 |
		^super.new.initTxalaMarkov2(dimension);
	}

	initTxalaMarkov2 { |adimension|
		dimension = adimension; // number or output values: 0,1,2,3,4
		this.reset();
	}

	reset {
		"reseting Markov Chain states".postln;
		mem = nil; // me last output, user last input <--
		options = Array.fill(dimension, {arg n=0; n}); // [0,1,2,3,4]
		//beatdata = Array.fillND(Array.fill(2, {options.size}), { 0 });
		beatdata = Array.fill(options.size, {[]});
		beatdata.do({arg data, index;
			beatdata[index] =
			   [[1,0,0,0,0],
				[0,1,0,0,0],
				[0,0,1,0,0],
				[0,0,0,1,0],
				[0,0,0,0,1]];
		});
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

		if (mem.isNil, { mem = [input, input] }); // first time

		beatdata[mem[1]][mem[0]][input] = beatdata[mem[1]][mem[0]][input] + 1;

		//mem = [detected]++mem[0..mem.size-2]; // prepend into memory the input before calc next value

		weights = beatdata[mem[0]][input];

		// now get the choices for that combination and calculate the next value
/*		weights.postln;
		weights.normalizeSum.postln;*/

		output = options.wchoose(weights.normalizeSum);

		if (output == 0 && input == 0, {
			output= 2;
/*			"0-0 detected".postln;
			if (weights[1..weights.size-1].sum == 0, { // first time the array is empty
				output = 2 // most common hit
			},{
				output = options[1..options.size-1].wchoose( weights[1..beatdata.size-1] );
			});*/
		});

		//mem.postln;
		mem = [output, input];//[num]++mem[0..mem.size-2]; // push right
		^output
	}
}
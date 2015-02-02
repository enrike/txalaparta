/* just first order markov chain with tipical txalaparta rhythm values. 0,1,2,3,4 hits
m = TxalaMarkov.new
m.next // first order markov chain with preset values in matrix
m.next2nd(4) // learning 2nd order markov chain
m.reset
*/



TxalaMarkov{

	var beatweigths, beatweights2nd, beatdata2nd, lasthits, options;

	*new {
		^super.new.initTxalaMarkov;
	}

	initTxalaMarkov {
		this.reset()
	}

	reset {
		lasthits = [2, 2]; // me, detected

		options = [0, 1, 2, 3, 4];

		beatweigths = [
			[0.0,  0.3,  0.4,  0.2,  0.1 ],
			[0.1,  0.3,  0.4,  0.2,  0.1 ],
			[0.05, 0.15, 0.6,  0.15, 0.05],
			[0.05, 0.1,  0.4,  0.3,  0.15],
			[0.0,  0.1,  0.4,  0.2,  0.3 ]
		];

		beatdata2nd = Array.fillND([5, 5, 5], { 0 }); // store here the data of changes
		beatweights2nd = Array.fillND([5, 5, 5], { 0 }); //store % of changes
	}

	next {
		var weights, curhits;
		weights = beatweigths[ lasthits[0] ];
		curhits = options.wchoose(weights.normalizeSum);
		lasthits[0] = curhits; // in this case only use the first slot because I know nothing about the detected beats
		^curhits;
	}

	next2nd { arg detected;
		var weights, curhits;

		if (detected.isNil, {detected=0});
		if (detected > 4, {detected = 4});
		if (detected < 0 , {detected = 0});

		this.updatematrix(detected);
		weights = beatweights2nd[lasthits[0]][lasthits[1]];
		curhits = options.wchoose(weights.normalizeSum);
		lasthits = [curhits, detected];
		^curhits;
	}

	updatematrix {arg hitnum;
		var total; //, state;
		beatdata2nd[lasthits[0]][lasthits[1]][hitnum] = beatdata2nd[lasthits[0]][lasthits[1]][hitnum] + 1; //increase this one in the data table
		total = beatdata2nd[lasthits[0]][lasthits[1]].sum; // total num of hits for that state

		beatweights2nd[lasthits[0]][lasthits[1]].size.do({arg index; //recalculate all % for that state
			beatweights2nd[lasthits[0]][lasthits[1]][index] = beatdata2nd[lasthits[0]][lasthits[1]][index] / total;
		})
	}
}
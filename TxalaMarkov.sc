// license GPL
// by www.ixi-audio.net

/* markov chain with tipical txalaparta rhythm values. defaults to 0,1,2,3,4 hits
m = TxalaMarkov.new
m.next // first order markov chain with preset values in beatweigths matrix
m.next2nd(4) // learning 2nd order markov chain
m.reset
*/



TxalaMarkov{

	var beatweigths, beatweights2nd, <>beatdata2nd, lasthits, options, dimension;

	*new { | adimension = 5 |
		^super.new.initTxalaMarkov(adimension);
	}

	initTxalaMarkov { |adimension|
		dimension = adimension;
		this.reset();
	}

	reset {
		lasthits = [2, 2]; // me, detected

		options = Array.fill(dimension, {arg n=0; n});

		beatweigths = [
			[0.0,  0.3,  0.4,  0.2,  0.1 ],
			[0.1,  0.3,  0.4,  0.2,  0.1 ],
			[0.05, 0.15, 0.6,  0.15, 0.05],
			[0.05, 0.1,  0.4,  0.3,  0.15],
			[0.0,  0.1,  0.4,  0.2,  0.3 ]
		];

		beatdata2nd = Array.fillND([options.size, options.size, options.size], { 0 }); // store here the data of changes
		beatweights2nd = Array.fillND([options.size, options.size, options.size], { 0 }); //store % of changes

		/*		2nd-order matrix for txalaparta beats
beat 0 1 2 3 4
00   N N N N N
01   N N N N N
02   N N N N N
03   N N N N N
04   N N N N N
10   N N N N N
11   N N N N N
12   N N N N N
13   N N N N N
14   N N N N N
20   N N N N N
21   N N N N N
22   N N N N N
23   N N N N N
24   N N N N N
30   N N N N N
31   N N N N N
32   N N N N N
33   N N N N N
34   N N N N N
40   N N N N N
41   N N N N N
42   N N N N N
43   N N N N N
44   N N N N N*/

	}

	new2ndmatrix {arg matrix;
		this.reset();
		beatdata2nd = matrix;
		// update the whole beatweights2nd matrix here
		beatweights2nd.do({arg row, n;
			row.do({arg slot, nn;
				slot.do({arg values, nnn;
					beatweights2nd[n][nn][nnn] = beatweights2nd[n][nn][nnn] / beatweights2nd[n][nn].sum
				});
			});
		});
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
/* just first order markov chain with tipical txalaparta rhythm values. 0,1,2,3,4 hits
m = TxalaMarkov.new
m.next
*/


	// beatdata = [ // store here the percentages of hits
	// 	[0, 0, 0, 0, 0 ],
	// 	[0, 0, 0, 0, 0 ],
	// 	[0, 0, 0, 0, 0 ],
	// 	[0, 0, 0, 0, 0 ],
	// 	[0, 0, 0, 0, 0 ]
	// ];

TxalaMarkov{

	var beatweigths, >lasthit;

	*new {
		^super.new.initTxalaMarkov;
	}

	initTxalaMarkov {
		this.reset()
	}

	reset {
		lasthit = 2; // [2, 2];

		beatweigths = [
			[0.0,  0.3,  0.4,  0.2,  0.1 ],
			[0.1,  0.3,  0.4,  0.2,  0.1 ],
			[0.05, 0.15, 0.6,  0.15, 0.05],
			[0.05, 0.1,  0.4,  0.3,  0.15],
			[0.0,  0.1,  0.4,  0.2,  0.3 ]
		];

		// beatdata2nd = Array.fillND([5, 5, 5], { 0 }); // store here the data of changes
		// beatweights2nd = Array.fillND([5, 5, 5], { 0 }); //store % of changes
	}


	next {
		var weights, curhit;
		weights = beatweigths[ lasthit ];
		curhit = [0,1,2,3,4].wchoose(weights.normalizeSum);
		lasthit = curhit;
		^curhit;
	}

	// updatematrixF(curPattern.size);
	// weights = beatweights2nd[lasthits[0]][lasthits[1]];

/*		updatematrixF {arg hitnum;
		var total; //, state;

		if ( hitnum.isNumber , {

			if (hitnum > 4, {hitnum = 4});
			if (hitnum < 0 , {hitnum = 0});

			beatdata2nd[lasthits[0]][lasthits[1]][hitnum] = beatdata2nd[lasthits[0]][lasthits[1]][hitnum] + 1; //increase this one in the data table
			total = beatdata2nd[lasthits[0]][lasthits[1]].sum; // total num of hits for that state

			beatweights2nd[lasthits[0]][lasthits[1]].size.do({arg index; //recalculate all % for that state
				beatweights2nd[lasthits[0]][lasthits[1]][index] = beatdata2nd[lasthits[0]][lasthits[1]][index] / total;
			});
		});
	}*/

}
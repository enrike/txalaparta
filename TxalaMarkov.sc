// license GPL
// by www.ixi-audio.net

/* markov chain with tipical txalaparta rhythm values. defaults to 0,1,2,3,4 hits
m = TxalaMarkov.new
m.next // first order markov chain with preset values in beatweigths matrix
m.update = true; //store data from input?
m.next2nd(3) // learning 2nd order markov chain. 2 compasses
m.next3rd(3) // 3 compasses
m.next4th(0) // 4 compasses
m.reset
*/



TxalaMarkov{

	var beatweigths;
	var <>beatdata2nd;
	var <>beatdata3rd;
	var <>beatdata4th;
	var lasthits, options, dimension, >update=true;

	*new { | dimension = 5 |
		^super.new.initTxalaMarkov(dimension);
	}

	initTxalaMarkov { |adimension|
		dimension = adimension; // number or output values: 0,1,2,3,4
		this.reset();
	}

	reset {
		"reseting Markov Chain states".postln;
		lasthits = [2, 2, 2, 2, 2, 2, 2]; // me, prev detected, prev me. 2 is most common number in txalaparta hits
		// this way it uses the information of the last two, three, or four compasses eg (2,3)(2,2)(1,2) as the current value is
		// input into the markov chain from the detection system

		options = Array.fill(dimension, {arg n=0; n});

		beatweigths = [ // this is just a fixed percetage data with some common behaviour
			[0.0,  0.3,  0.4,  0.2,  0.1 ], // returning 0 to a 0 input is illegal
			[0.1,  0.3,  0.4,  0.2,  0.1 ],
			[0.05, 0.15, 0.6,  0.15, 0.05],
			[0.05, 0.1,  0.4,  0.3,  0.15],
			[0.1,  0.1,  0.4,  0.2,  0.2 ]
		];

		beatdata2nd = Array.fillND(Array.fill(3, {options.size}), { 0 }); // store here the data of changes

		beatdata3rd = Array.fillND(Array.fill(5, {options.size}), { 0 }); // store here the data of changes

		beatdata4th = Array.fillND(Array.fill(7, {options.size}), { 0 }); // store here the data of changes

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
44   N N N N N
		*/
	}

	next {
		var weights, curhits;
		weights = beatweigths[ lasthits[0] ]; // last me
		curhits = options.wchoose(weights.normalizeSum);
		lasthits[0] = curhits; // in this case just use the first slot because I know nothing about the detected beats
		^curhits;
	}

	loaddata{ arg data;
		this.reset();
		beatdata2nd = data;
	}

	sanitycheck {arg detected;
		if (detected>=dimension, {detected = options.last}); // max limit
		if ( (detected==0) && (lasthits[0]==0), { // this is illegal and humans would never do it. more likely to be an error
			detected = options[1..].choose;
		});
		^detected;
	}

	next2nd{ arg detected;
		detected = this.sanitycheck(detected);
		beatdata2nd[lasthits[1]][lasthits[0]][detected] = beatdata2nd[lasthits[1]][lasthits[0]][detected] + 1; // increase this slot
		^options.wchoose(beatdata2nd[lasthits[1]][lasthits[0]].normalizeSum); // get row's data normalized to 0-1 percentage
	}

	next3rd{ arg detected;
		detected = this.sanitycheck(detected);
		beatdata3rd[lasthits[3]][lasthits[2]][lasthits[1]][lasthits[0]][detected] = beatdata3rd[lasthits[3]][lasthits[2]][lasthits[1]][lasthits[0]][detected] + 1;
		^options.wchoose(beatdata3rd[lasthits[3]][lasthits[2]][lasthits[1]][lasthits[0]].normalizeSum);
	}

	next4th{ arg detected;
		detected = this.sanitycheck(detected);
		beatdata4th[lasthits[5]][lasthits[4]][lasthits[3]][lasthits[2]][lasthits[1]][lasthits[0]][detected] = beatdata4th[lasthits[5]][lasthits[4]][lasthits[3]][lasthits[2]][lasthits[1]][lasthits[0]][detected] + 1;
		^options.wchoose(beatdata4th[lasthits[5]][lasthits[4]][lasthits[3]][lasthits[2]][lasthits[1]][lasthits[0]].normalizeSum);
	}
}
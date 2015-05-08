// license GPL
// by www.ixi-audio.net

/* Stores planks data in pattern

*/

TxalaPatternBank{
	var bank;

	*new {
		^super.new.initTxalaPatternBank();
	}

	initTxalaPatternBank {
		bank = [[],[],[],[]];
	}

	getrandpattern{ arg numhits=2;
		var pat=nil;
		if (numhits>0, { pat = bank[numhits-1].choose() });
		^pat;
	}

	addpattern{ arg apattern;
		var blueprint = "", isthere=false, newpattern=(), size = apattern.size;

		apattern.collect({ arg item; item.plank.asString }).do({arg item; blueprint = blueprint++item });// get pattern blueprint

		newpattern = newpattern.add(\blueprint->blueprint); // plank sequence. 4123, 13, 343, 114 or 22 for instance
		newpattern = newpattern.add(\pattern->apattern);
		newpattern = newpattern.add(\numtimes->1); // to count how many times has appeared this blueprintapattern.size-1

		if (apattern.isNil.not, { // sometimes I get patterns which are nil. WHY?
			bank[apattern.size-1] = bank[apattern.size-1].add(newpattern);
		},{
			["pattern->", apattern].postln;
		});



		// likely to be a better way to deal with this
/*		isthere = bank[apattern.size-1].every({ arg item; item.blueprint == blueprint });// already there?
		if (isthere, {
			bank[apattern.size-1].do({arg pat, index;
				if (pat.pattern.blueprint == blueprint, { // just increase the count
					bank[apattern.size-1][index].numtimes = bank[apattern.size-1][index].numtimes +1;
				})
			})
		},{
			bank[apattern.size-1] = bank[apattern.size-1].add(newpattern);
		});*/

		//["sizeis",bank[apattern.size-1].size].postln;

	}
}


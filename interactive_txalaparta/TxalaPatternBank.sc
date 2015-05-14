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
		this.reset();
	}

	reset{
		bank = [[],[],[],[]];
	}

	getrandpattern{ arg numhits=2;
		var pat="";
		if (numhits > 0, { pat = bank[numhits-1].choose() });
		if (pat=="", { // never return nil
			Array.fill(numhits, {numhits.rand+1}).do({arg item; pat=pat++item}) // just produce a random blueprint
		});
		^pat;
	}

	addpattern{ arg apattern;
		var blueprint = "", isthere=false, newpattern=(), size = apattern.size;

		// get pattern blueprint
		apattern.collect({ arg item; item.plank.asString }).do({arg item; blueprint = blueprint++item });

		newpattern = newpattern.add(\blueprint -> blueprint); // plank sequence. 4123, 13, 343, 114 or 22 for instance
		newpattern = newpattern.add(\pattern -> apattern);
		newpattern = newpattern.add(\numtimes -> 1); // to count how many times has appeared this blueprintapattern.size-1

		if (apattern.isNil.not, { // sometimes I get patterns which are nil. WHY?
			bank[apattern.size-1] = bank[apattern.size-1].add(newpattern);
		});
	}
}


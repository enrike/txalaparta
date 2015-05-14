// license GPL
// by www.ixi-audio.net
/* stores the abs value of phrases and uses that to return a weight choice of the available options
m = TxalaWChoose.new
m.next(2)
m.next(0)
m.reset
*/
TxalaWChoose{
	var beatdata;

	var options, dimension;
	*new { | dimension = 5 |
		^super.new.initTxalaWChoose(dimension);
	}
	initTxalaWChoose { |adimension|
		dimension = adimension; // number or output values: 0,1,2,3,4
		options = Array.fill(dimension, {arg n=0; n});
		this.reset();
	}
	reset {
		"reseting data".postln;
		beatdata = Array.fill(dimension, {0});
	}
	next{ arg input;
		var output;

		beatdata[input] = beatdata[input] + 1; // one more of this type
		output = options.wchoose( beatdata.normalizeSum );

		// this is probably not very elegant way of avoiding output 0 to a 0 input.
		if (output == 0 && input == 0, {
			"0-0 detected".postln;
			if (beatdata[1..beatdata.size-1].sum == 0, { // first time the array is empty
				output = 2 // most common hit
			},{
				output = options[1..options.size-1].wchoose( beatdata[1..beatdata.size-1].normalizeSum );
			});
		});

		^output;
	}
}
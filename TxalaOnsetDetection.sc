// license GPL
// by www.ixi-audio.net

/*
t = TxalaOnsetDetection.new(nil, s)
to do: improve detected amplitude range and values, plank detection
*/

TxalaOnsetDetection{

	var server, parent, <curPattern, <synth, synthOSCcb, >processflag, <patternsttime, sttime;
	var <>plankdata;// up to 6 planks
	var features;

	*new {| aparent=nil, aserver |
		^super.new.initTxalaOnsetDetection(aparent, aserver);
	}

	initTxalaOnsetDetection { arg aparent, aserver;
		parent = aparent;
		server = aserver;
		plankdata = [[],[],[],[],[],[]];
		features = [\chroma, \freq, \keyt];
		this.reset()
	}

	reset {
		processflag = false;
		patternsttime = 0;
		curPattern = nil;
		sttime = SystemClock.seconds;
		plankdata = parent.plankdata; // if anything
		this.doAudio();
	}

	kill {
		synth.free;
		synth = nil;
		OSCdef(\txalaonsetOSCdef).clear;
		OSCdef(\txalaonsetOSCdef).free;
	}

	closegroup { // group ended detected by silence detector. must return info about the pattern played and clear local data.
		var pat = curPattern;
		curPattern = nil;
		^pat;
	}

	doAudio {
		this.kill(); // force

		SynthDef(\txalaonsetlistener, { |in=0, threshold=0.6, relaxtime=2.1, floor=0.1, mingap=1, offset=0.11|
		 	var fft, onset, chroma, keyt, signal, level=0, freq=0, hasfreq=false, del;
		 	signal = SoundIn.ar(in);
			level = Amplitude.kr(signal);
		 	fft = FFT(LocalBuf(2048), signal);
			chroma = Chromagram.kr(fft, 2048);
		 	onset = Onsets.kr(fft, threshold, \rcomplex, relaxtime, floor, mingap, medianspan:11, whtype:1, rawodf:0);
			keyt = KeyTrack.kr(fft, 0.01, 0.0); //(chain, keydecay: 2, chromaleak: 0.5)
			# freq, hasfreq = Tartini.kr(signal,  threshold: 0.93, n: 2048, k: 0, overlap: 1024, smallCutoff: 0.5 );

			del = DelayN.kr(onset, offset, offset);// CRUCIAL. percussive sounds are too chaotic at the beggining

			SendReply.kr(del, '/txalaonset', (chroma++[level, hasfreq, freq, keyt]));
		 }).add;

		{
			synth = Synth(\txalaonsetlistener, [
				\in, ~listenparemeters.in,
				\amp, ~listenparemeters.amp,
				\threshold, ~listenparemeters.onset.threshold,
				\relaxtime, ~listenparemeters.onset.relaxtime,
				\floor, ~listenparemeters.onset.floor,
				\mingap, ~listenparemeters.onset.mingap
			]);
		}.defer(0.5);

		OSCdef(\txalaonsetOSCdef, {|msg, time, addr, recvPort| this.process(msg)}, '/txalaonset', server.addr);
	}

	process { arg msg;
		var hitdata, hittime, plank=0, chroma, level, hasfreq, freq, keyt;

		msg = msg[3..]; // remove OSC data

		// (chroma++[level, hasfreq, freq, keyt])
		chroma  = msg[0..11]; //chroma 12 items
		level   = msg[12];    //level
		hasfreq = msg[13];    //hasfreq
		freq    = msg[14];    //freq
		keyt    = msg[15];    //keyt
		//msg.size.postln;//16

		if (processflag.not, { // if not answering myself
			if (curPattern.isNil, { // this is the first hit of a new pattern
				hittime = 0; // start counting on first one
				patternsttime = SystemClock.seconds;
				parent.broadcastgroupstarted(); //
			},{
				hittime = SystemClock.seconds - patternsttime; // distance from first hit of this group
			});

			if (~plankdetect, {
				var off;
				//if ( hasfreq.asBoolean, {
				var data = (); // this does need some short of normalization
				data.add(\chroma -> chroma); // 12 items
				data.add(\freq -> freq);
				data.add(\keyt -> keyt);

/*              ["chroma", chroma].postln;
				["freq", freq].postln;
				["keyt", keyt].postln;
				["level", level].postln; */

				if (~recindex.isNil.not, { // extract data from plank into ~recindex slot
					["storing plank data into", ~recindex, data].postln;
					plankdata[~recindex] = data; // stores everything
					off = parent.pitchbuttons[~recindex];
					~recindex = nil;
					{ off.value = 0 }.defer; // off button
				},{ // plank analysis
					plank = this.matchplank(data);
					plank.postln;
				})
				//});
			});

			hitdata = ().add(\time -> hittime)
			            .add(\amp -> level)
			            .add(\player -> 1) //always 1 in this case
			            .add(\plank -> plank);
			curPattern = curPattern.add(hitdata);

			if (parent.isNil.not, { parent.newonset( (patternsttime + hittime), level, 1, plank) });
		});
	}

	numactiveplanks{
		var num = 0;
		plankdata.do({arg arr; // there is a proper way to do this but i cannot be bothered with fighting with the doc system
			if (arr.size.asBoolean, {num=num+1});
		});
		["active planks are", num].postln;
		if (num==0, {num=1}); //score need one line at least
		^num
	}

	matchplank {arg data;
		var fdata, plank, res = Array.new(plankdata.size); //res = Array.fill(plankdata.size, {0}); // all planks
		fdata = data.atAll(features).flat; //filtered data. flat not need

		plankdata.do({ arg dataset;
			var fdataset;
			if (dataset.size.asBoolean, {
				fdataset = dataset.atAll(features).flat;
				res = res.add( (fdata-fdataset).abs.sum );
			});
		});
		plank = res.minIndex;
		if (plank.isNil, {plank = 0; "could not find out".postln});
		^plank
		//^if(res.minIndex.isNil, {0},{res.minIndex})
	}
}
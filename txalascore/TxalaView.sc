/*
class not used any longer??
*/

TxalaView {

	var view, events, selected, timeoffset;
	var timeframe = 12;

	*new {|parent, rect, numPlanks=3|
		^super.new.initTxalaScore( parent, rect, numPlanks );
	}

	initTxalaScore {|parent, rect, numPlanks|
		var plankheight;
		selected = nil;
		view = UserView.new(parent, rect);
		view.background = Color.white;
		plankheight = (view.bounds.height/(numPlanks+1));		view.drawFunc_({
			// the planks
			(numPlanks).do({arg i;
				Pen.line(Point(0, plankheight*(i+1)), Point(view.bounds.width,plankheight*(i+1)));
			});
			Pen.stroke;
			// the time grid (vertical lines)
			Pen.color = Color.black.alpha_(0.2);
			20.do({arg i;
				Pen.line(Point((view.bounds.width/20)*i, 0), Point((view.bounds.width/20)*i, view.bounds.height));
			});
			Pen.stroke;
			// the events themselves
			Pen.color = Color.black;
			events.do({arg event;
				var time = event.time * view.bounds.width;
			//	var time = (event.time-timeoffset) * (view.bounds.width);
				Pen.color = if(event.player == 1, {Color.red.alpha_(0.5)}, {Color.blue.alpha_(0.5)});
				Pen.fillRect(Rect(time-4, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-4)).abs, 8, 8));
				Pen.color = Color.black;
				Pen.line(Point(time, (view.bounds.height-(plankheight*event.plank)).abs), Point(time, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-8)).abs));
				Pen.addRect(Rect(time-4, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-4)).abs, 8, 8));
				Pen.stroke;
			});
		});
		view.mouseDownAction_({|view, x, y, mod|
			selected = nil;
			block{arg break;
				events.do({ arg event, i;
					var rect = Rect(event.time * view.bounds.width-4, (view.bounds.height-((event.amp*plankheight) + (plankheight*event.plank)-4)).abs, 8, 8);
					if(rect.contains(Point(x,y)), {
						 "Inside : ".post; i.postln;
						selected = i;
						break.value();
					});
				});
			};
			if(mod == 262401, {
				events = events.add(().add(\time -> (x/view.bounds.width)).add(\amp -> (y/view.bounds.height)));
				this.update(events);
				view.update;
			});
		});
		view.mouseMoveAction_({|view, x, y|
			if(selected.isNil.not, {
				events[selected].time = (x/view.bounds.width).postln;

				events[selected].amp = ((view.bounds.height-y).abs).linlin( events[selected].plank*plankheight , (events[selected].plank+1)*plankheight, 0, 1).postln;
				this.update(events);
				view.update;
			});

		});
		view.mouseUpAction_({ this.sortEvents });
		view.keyDownAction_({|view, key, sm, wh|
			[view, key, sm, wh].postln;
			if(wh == 127, {
				events.removeAt(selected);
				this.update(events);
				view.update;
			});
		});

	}

	sortEvents {
		events = events.sort({arg e1, e2; e1.time <= e2.time });
	}

	update { |arr |
		events = arr;
		view.refresh;
	}

	postEvents {
		" EVENTS ____________________ \n".postln;
		events.postln;
	}

}

/*

e = {arg i; ().add(\time -> (0.1+ (i/10))).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1))}!8;
w = Window.new("txalascore", Rect(100, 100, 800, 500)).front;
x = TxalaView.new(w, Rect(10, 10, 600, 400));
x.update(e);

x.postEvents;


e = {arg i; ().add(\time -> (0.1+ (i/10))).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1))}!8;
o = e.reject({arg event; event.player == 1})
t = e.reject({arg event; event.player == 0})

fork{
	20.do({
		e = {arg i; ().add(\time -> (0.1+ (i/10))).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1))}!8;
{x.update(e)}.defer;

		2.wait;
		})
	}

o = e.reject({arg event; event.player == 1})
t = e.reject({arg event; event.player == 0})


// ////////////////////

w = Window.new("txalascore", Rect(100, 100, 1400, 500)).front;
x = TxalaScore.new(w, Rect(10, 10, 1400, 400), 3);

e = [];
t = Main.elapsedTime;

fork{
	inf.do({arg i;
		var newtime = Main.elapsedTime - t;
		e = e.add(().add(\time -> newtime).add(\amp -> rrand(0.1, 0.9)).add(\player -> (2.rand+1)).add(\plank -> (3.rand+1)));
	0.1.wait;
	});
};


fork{
	inf.do({arg i;
		var now = Main.elapsedTime - t;
		{x.update(e, now)}.defer;
		0.05.wait;
		})
	};

x.timeframe =6

*/


"""Summarize trace phases and client update intervals without inferring causality."""
import csv
import sys
from collections import Counter, defaultdict


def summarize(path):
    phases = Counter()
    updates = defaultdict(list)
    comments = []
    with open(path, encoding='utf-8') as source:
        def records():
            for line in source:
                if line.startswith('#'):
                    comments.append(line.strip())
                else:
                    yield line
        for row in csv.DictReader(records()):
            phases[row['phase']] += 1
            if row['phase'] == 'sync_before':
                updates[row['train'], row['index']].append(int(row['mono_ns']))
    print('Phases:', dict(phases))
    for (train, index), times in sorted(updates.items()):
        gaps = sorted((b - a) / 1e6 for a, b in zip(times, times[1:]))
        if gaps:
            print(f'{train} carriage={index}: n={len(times)}, '
                  f'apply interval ms p50={gaps[len(gaps)//2]:.1f} '
                  f'p95={gaps[min(len(gaps)-1, int(len(gaps)*.95))]:.1f} max={gaps[-1]:.1f}')
    for comment in comments:
        print(comment)


if __name__ == '__main__':
    for filename in sys.argv[1:]:
        print(filename)
        summarize(filename)

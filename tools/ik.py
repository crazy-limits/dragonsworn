"""Small numeric IK on top of `rig.Rig`: damped least squares (Levenberg-Marquardt) with a
finite-difference Jacobian. Exact enough for keyframes (residuals converge to ~1e-6 px) and generic,
so a limb is described only by which bone angles may move and what its residuals are.
"""
import math


def solve(residuals, x0, iterations=60, damping=1e-3, eps=1e-4, tol=1e-9):
	"""Minimise sum(r_i(x)^2). `residuals(x)` returns a list of floats."""
	x = list(x0)
	r = residuals(x)
	cost = sum(v * v for v in r)
	lam = damping
	for _ in range(iterations):
		if cost < tol:
			break
		n, m = len(x), len(r)
		J = [[0.0] * n for _ in range(m)]
		for j in range(n):
			xp = list(x)
			xp[j] += eps
			rp = residuals(xp)
			for i in range(m):
				J[i][j] = (rp[i] - r[i]) / eps
		# normal equations (J^T J + lam diag) dx = -J^T r
		A = [[sum(J[k][i] * J[k][j] for k in range(m)) for j in range(n)] for i in range(n)]
		g = [sum(J[k][i] * r[k] for k in range(m)) for i in range(n)]
		improved = False
		for _ in range(12):
			M = [row[:] for row in A]
			for i in range(n):
				M[i][i] += lam * (1 + A[i][i])
			dx = _solve_linear(M, [-v for v in g])
			xn = [x[i] + dx[i] for i in range(n)]
			rn = residuals(xn)
			cn = sum(v * v for v in rn)
			if cn < cost:
				x, r, cost = xn, rn, cn
				lam = max(lam / 3, 1e-9)
				improved = True
				break
			lam *= 4
		if not improved:
			break
	return x, cost


def _solve_linear(A, b):
	n = len(b)
	M = [A[i][:] + [b[i]] for i in range(n)]
	for c in range(n):
		p = max(range(c, n), key=lambda r: abs(M[r][c]))
		M[c], M[p] = M[p], M[c]
		if abs(M[c][c]) < 1e-15:
			continue
		for r in range(n):
			if r != c:
				f = M[r][c] / M[c][c]
				for k in range(c, n + 1):
					M[r][k] -= f * M[c][k]
	return [M[i][n] / M[i][i] if abs(M[i][i]) > 1e-15 else 0.0 for i in range(n)]


def sub(a, b):
	return [a[i] - b[i] for i in range(3)]


def norm(a):
	l = math.sqrt(sum(v * v for v in a))
	return [v / l for v in a] if l > 0 else a

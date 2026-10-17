import { BackendStatus } from "@/components/BackendStatus";
import { ClientSearchForm } from "@/components/ClientSearchForm";

export default function HomePage() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-10 px-4 py-12 sm:px-6 sm:py-16">
      <header className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">Hotel Deal Agent</h1>
          <BackendStatus />
        </div>
        <p className="text-lg text-slate-600">Finds and negotiates the cheapest suitable hotel in Ontario</p>
        <p className="text-sm text-slate-500">
          Pick a city, your dates and guests. The agent phones the most promising hotels, asks for
          their lowest all-in direct rate, and emails you the result with the call transcript.
        </p>
      </header>

      <section aria-labelledby="search-heading" className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
        <h2 id="search-heading" className="mb-6 text-xl font-semibold">
          Start a search
        </h2>
        <ClientSearchForm />
      </section>
    </main>
  );
}
